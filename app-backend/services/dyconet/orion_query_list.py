"""Request-scoped Weather QueryList from Orion with explicit spatial filtering.

Other entity families keep the existing Orion provider contract. This is not
a process-global sensor cache: each deliberation retrieves a new list.
"""
from dataclasses import replace
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
    }
    return actual is None or str(actual).strip().lower() in aliases[expected]


class OrionQueryListProvider(OrionContextProvider):
    def __init__(self, **kwargs):
        kwargs.setdefault('timeout', 15.0)
        super().__init__(**kwargs)
        self._weather_list = None
        self._weather_error = None

    def observations(self, entity, *, variables=None):
        if entity.entity_id != WINFRED_ID:
            return super().observations(entity, variables=variables)
        names = tuple(variables) if variables is not None else EXPECTED_ATTRIBUTES['Weather']
        output = []
        for name in names:
            field = WINFRED_FIELDS.get(name)
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
            output.append(replace(observation, variable=name, source_metadata=metadata))
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
            for observation in self.observations(entity, variables=EXPECTED_ATTRIBUTES['Weather']):
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
