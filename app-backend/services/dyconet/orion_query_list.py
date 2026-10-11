"""Request-scoped Weather QueryList from Orion with explicit spatial filtering.

Other entity families keep the existing Orion provider contract. This is not
a process-global sensor cache: each deliberation retrieves a new list.
"""
from dataclasses import replace
from datetime import datetime, timezone
import math
from orion_context import (
    OrionContextProvider, OrionProviderError, EXPECTED_ATTRIBUTES,
    _coordinate_from_query, _distance_meters, _location_metadata, _attribute_value,
)

# Winfred's frame decoder matches the manufacturer S2120 protocol. These
# units are a sensor contract, NOT statistical imputation of missing readings.
WINFRED_ID = 'Sensor:Weather:Winfred'
WINFRED_FIELDS = {
    'temperature': ('temperature', '°C', -40, 80),
    'humidity': ('humidity', '%', 0, 100),
    'rain': ('rainIntensity', 'mm/h', 0, 450),
    'windSpeed': ('windSpeed', 'm/s', 0, 50),
    'windGust': ('peakWindGust', 'm/s', 0, 50),
    'uvIndex': ('lightUV', 'index', 0, 16),
    'lightIntensity': ('lightIntensity', 'lux', 0, 200000),
    'cumulativeRainfall': ('cumulativeRainfall', 'mm', 0, 999999999),
}
CONTRACT_SOURCES = [
    'https://github.com/imiq-project/infrastructure/blob/05ed18dba94958272a733eca41853a56b0aebb5c/services/sensors/src/main.go',
    'https://github.com/imiq-project/infrastructure/blob/05ed18dba94958272a733eca41853a56b0aebb5c/services/sensors/src/sensecap.go',
    'https://www.seeed.cc/product/sensecap-s2120-8-in-1-lorawan-weather-sensor',
]


def unit_matches(actual, expected):
    aliases = {
        '°C': {'°c','c','celsius','cel'}, '%': {'%','percent','percentage'},
        'm/s': {'m/s','mps'}, 'mm/h': {'mm/h','mm/hour','mm/hr'},
        'lux': {'lux','lx'}, 'index': {'index','1','dimensionless'},
        'mm': {'mm','millimetres','millimeters'},
    }
    return actual is None or str(actual).strip().lower() in aliases[expected]


class OrionQueryListProvider(OrionContextProvider):
    def __init__(self, **kwargs):
        kwargs.setdefault('timeout', 15.0)
        super().__init__(**kwargs)
        self._weather_list = None
        self._weather_error = None
        self._weather_received_at = None

    def observations(self, entity, *, variables=None):
        if entity.entity_id != WINFRED_ID:
            output = super().observations(entity, variables=variables)
            # Walter t1solrad is not a confirmed lux contract. New queries
            # must not infer illuminance from that alias; old snapshots stay raw.
            corrected = []
            for o in output:
                if o.variable == 'lightIntensity' and entity.entity_id == 'Sensor:Weather:Walter':
                    valid = o.unit in ('lux', 'lx') and o.numeric_value is not None and 0 <= o.numeric_value <= 200000
                    o = replace(o, unit='lux' if valid else o.unit, source_metadata={
                        **dict(o.source_metadata or {}),
                        'source_attribute': 'lightIntensity', 'provider_ingest_attribute': 't1solrad',
                        'source_unit': o.unit, 'unit_provenance': 'EXPLICIT' if o.unit else 'UNKNOWN',
                        'source_contract_confidence': 'CONFIRMED' if valid else 'UNKNOWN',
                        'normalization_eligible': valid,
                        'light_unit_audit': 'EXPLICIT_LUX' if valid else 'WALTER_SOLAR_RADIATION_UNIT_UNVERIFIED',
                    })
                corrected.append(o)
            return tuple(corrected)
        names = tuple(variables) if variables is not None else EXPECTED_ATTRIBUTES['Weather']
        output = []
        for name in names:
            field = WINFRED_FIELDS.get(name)
            valid = False
            source_name = field[0] if field else name
            # Do not substitute a different field when the source reading is absent.
            observation = super().observations(entity, variables=(source_name,))[0]
            metadata = dict(observation.source_metadata or {})
            if field and source_name not in entity.attributes:
                metadata.update({'normalization_eligible': False, 'source_attribute': source_name,
                                 'canonical_variable': name, 'source_contract_confidence': 'UNKNOWN'})
            if field and source_name in entity.attributes:
                unit, lower, upper = field[1:]
                value = observation.numeric_value
                valid = value is not None and math.isfinite(value) and lower <= value <= upper and unit_matches(observation.unit, unit)
                metadata.update({
                    'source_contract_confidence': 'CONFIRMED' if valid else 'UNKNOWN',
                    'normalization_eligible': valid, 'source_sensor_model': 'SenseCAP S2120',
                    'source_attribute': source_name, 'canonical_variable': name,
                    'source_unit': observation.unit,
                    'unit_provenance': 'EXPLICIT' if observation.unit else 'VALIDATED_DEVICE_CONTRACT',
                    'source_contract_references': CONTRACT_SOURCES,
                    'source_contract_valid_range': [lower, upper],
                    'temporal_freshness': 'UNKNOWN' if observation.timestamp is None else 'NOT_ASSESSED',
                })
                observation = replace(observation, unit=unit if valid else observation.unit)
            if name in ('rain', 'cumulativeRainfall'):
                interval = _attribute_value(entity.attributes.get('uplinkInterval'))[0]
                try:
                    interval = float(interval)
                    if not math.isfinite(interval) or not 1 <= interval <= 1440:
                        interval = None
                except (ValueError, TypeError):
                    interval = None
                # Neither Walter's clock, query reception nor dateModified
                # identifies this station's last acquisition/report.
                raw_attribute = entity.attributes.get(source_name)
                wrapper = raw_attribute if isinstance(raw_attribute, dict) else {}
                raw_metadata = wrapper.get('metadata') if isinstance(wrapper.get('metadata'), dict) else {}
                report = wrapper.get('receivedAt') or raw_metadata.get('receivedAt')
                if isinstance(report, dict):
                    report = report.get('value')
                try:
                    parsed_report = datetime.fromisoformat(str(report).replace('Z', '+00:00'))
                    report = parsed_report.isoformat() if parsed_report.tzinfo else None
                except (TypeError, ValueError):
                    report = None
                metadata.update({
                    'rain_contract_version': 'winfred-rain-intensity-v1',
                    'rain_display_eligible': name == 'rain' and valid,
                    'rain_use': 'LAST_REPORTED_INTENSITY' if name == 'rain' else 'INTERVAL_AUDIT_ONLY',
                    'measurement_window_minutes': 10 if name == 'rain' else None,
                    'measurement_window_seconds': 600 if name == 'rain' else None,
                    'measurement_semantics': 'TEN_MINUTE_ACCUMULATION_SCALED_TO_HOURLY_RATE' if name == 'rain' else 'RESETTABLE_CUMULATIVE_COUNTER',
                    'reporting_interval_minutes': interval,
                    'last_report_at': report,
                    'last_report_time_provenance': 'EXPLICIT_ATTRIBUTE_RECEIVED_AT' if report else 'UNKNOWN',
                    'measurement_time_known': False,
                })
                if name == 'cumulativeRainfall':
                    metadata['normalization_eligible'] = False
            output.append(replace(observation, variable=name, source_metadata=metadata))
        # The project owner confirmed the provider's shared observation clock:
        # Walter timestamps both stations. Bind only within the same QueryList;
        # never borrow the current date or an older request's timestamp.
        walter = next((e for e in (self._weather_list or ()) if e.entity_id == 'Sensor:Weather:Walter'), None)
        shared = _attribute_value(walter.attributes.get('datetime'))[0] if walter else None
        if isinstance(shared,str):
            output = [replace(o,timestamp=shared,source_metadata={
                **dict(o.source_metadata or {}), 'timestamp_reference_entity':'Sensor:Weather:Walter',
                'timestamp_raw':shared, 'timestamp_confidence':'PROVIDER_SHARED_CLOCK_CONFIRMED_BY_AUTHOR',
                'timestamp_reference_attribute':'datetime',
                'timestamp_provenance':'PROVIDER_SHARED_CLOCK_CONFIRMED_BY_AUTHOR',
                'timezone_provenance':'AUTHOR_CONFIRMED_EUROPE_BERLIN'})
                if o.timestamp is None and o.numeric_value is not None else o for o in output]
        return tuple(output)

    def get_context(self, family, *, nearest_only=False, **query):
        if family != 'weather':
            return super().get_context(family, nearest_only=nearest_only, **query)
        target = _coordinate_from_query(query.get('coords'))
        if target is None or query.get('geometry') != 'point':
            return super().get_context(family, nearest_only=nearest_only, **query)
        georel = query.get('georel', '')
        if not georel.startswith('near;maxDistance:'):
            return super().get_context(family, nearest_only=nearest_only, **query)
        radius = float(georel.split(':', 1)[1])
        if self._weather_error is not None:
            raise self._weather_error
        if self._weather_list is None:
            try:
                result = self.query_entities(entity_type='Weather', limit=100)
                # Never treat a potentially truncated QueryList as complete.
                if len(result.entities) >= 100:
                    raise OrionProviderError('Weather QueryList reached its retrieval cap')
                self._weather_list = result.entities
                self._weather_received_at = datetime.now(timezone.utc).isoformat()
            except OrionProviderError as exc:
                self._weather_error = exc
                raise
        located = []
        for entity in self._weather_list:
            raw_location = entity.attributes.get('location')
            location = _location_metadata(_attribute_value(raw_location)[0]) or {}
            if location.get('usable', True) and 'latitude' in location and 'longitude' in location:
                distance = _distance_meters(target, (location['latitude'], location['longitude']))
                if distance <= radius:
                    located.append((distance, entity, location))
        located.sort(key=lambda entry: (entry[0], entry[1].entity_id))
        if nearest_only:
            located = located[:1]
        observations = []
        for distance, entity, location in located:
            names = EXPECTED_ATTRIBUTES['Weather'] + (('cumulativeRainfall',) if entity.entity_id == WINFRED_ID else ())
            for observation in self.observations(entity, variables=names):
                if self._weather_received_at is not None:
                    from comfort_pilot.sensor_time import attach_reception
                    stamped = attach_reception(observation.to_dict(), self._weather_received_at)
                    observation = replace(observation, source_metadata=stamped.get('source_metadata'))
                observations.append(replace(observation, spatial_metadata={
                    **dict(observation.spatial_metadata or {}), 'location': location,
                }, source_metadata={
                    **dict(observation.source_metadata or {}),
                    'query_method':'ORION_ENTITY_LIST_LOCAL_RADIUS',
                    'query_list_type':'Weather', 'query_list_entity_count':len(self._weather_list),
                    'distance_to_segment_midpoint_meters':distance, 'query_radius_meters':radius,
                    'nearest_only':nearest_only,
                }))
        return tuple(observations)
