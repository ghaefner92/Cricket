"""V2: distinguish report age from query age and preserve excluded measurements."""
from copy import deepcopy
import math
from journey_contract import timestamp
from .sensor_time import reception_check
from .weather_contract import INTENSITY, RAW_V2, FEATURES_V2, UNITS_V2, POLICY, design_row_v2


def audit(context, as_of):
    queried = timestamp(context['queried_at'])
    if queried > timestamp(as_of): raise ValueError('Context query is later than prediction')
    values = dict.fromkeys(RAW_V2)
    readings, warnings = [], []
    for item in context.get('measurements', []):
        metadata = item.get('source_metadata') or {}
        original = item.get('variable')
        key = 'rainIntensity' if original == 'rain' else original
        value = item.get('numeric_value')
        reason, report_age, measurement_age = None, None, None
        receipt = reception_check(item, queried)
        if key not in RAW_V2:
            reason = 'UV excluded by protocol' if key == 'uvIndex' else 'unsupported_measurement'
        elif (item.get('status') != 'OBSERVED' or item.get('unit') != UNITS_V2[key]
              or not item.get('source') or type(value) not in (int, float) or not math.isfinite(value)):
            reason = 'invalid_value_status_source_or_unit'
        elif metadata.get('normalization_eligible') is False:
            reason = 'provider_contract_ineligible'
        elif key == 'rainIntensity':
            if (item.get('source_entity_id') != 'Sensor:Weather:Winfred'
                or str(item.get('source')).lower() != 'orion'
                or metadata.get('source_attribute') != 'rainIntensity'):
                reason = 'not_winfred_intensity'
            elif (metadata.get('rain_contract_version') != 'winfred-rain-intensity-v1'
                  or metadata.get('measurement_window_seconds') != 600
                  or metadata.get('measurement_semantics') != 'TEN_MINUTE_ACCUMULATION_SCALED_TO_HOURLY_RATE'):
                reason = 'rain_window_or_semantics_unverified'
            elif (metadata.get('last_report_time_provenance') != 'EXPLICIT_ATTRIBUTE_RECEIVED_AT'
                  or not metadata.get('last_report_at')):
                reason = 'rain_report_time_unknown'
            else:
                try: report = timestamp(metadata['last_report_at'])
                except (ValueError, TypeError): reason = 'rain_report_time_invalid'
                else:
                    if report > queried: raise ValueError('Rain report is later than frozen query')
                    report_age = (queried - report).total_seconds()
                    if report_age > POLICY['max_rain_report_age_seconds']:
                        reason = 'rain_report_stale_under_candidate_policy'
            if reason is None and receipt is not None: reason = receipt['reason']
        elif (key == 'lightIntensity' and item.get('source_entity_id') == 'Sensor:Weather:Walter'
              and (metadata.get('pilot_original_unit', item.get('unit')) not in ('lux', 'lx')
                   or metadata.get('pilot_unit_provenance') == 'provider_contract'
                   or metadata.get('unit_provenance') == 'INFERRED')):
            reason = 'walter_light_unit_unverified'
        elif receipt is not None:
            reason = receipt['reason']
        elif not item.get('timestamp'):
            reason = 'measurement_time_unknown'
        else:
            try: measured = timestamp(item['timestamp'])
            except (ValueError, TypeError): reason = 'measurement_time_invalid'
            else:
                if measured > queried: raise ValueError('Observation is later than query')
                measurement_age = (queried - measured).total_seconds()
                if measurement_age > POLICY['max_other_measurement_age_seconds']:
                    reason = 'stale_measurement'
        if reason is None:
            try: design_row_v2({key: value})
            except ValueError: reason = 'outside_measurement_domain'
        if reason is None:
            if values[key] is not None: raise ValueError('Duplicate contextual variable')
            values[key] = value
        else: warnings.append(str(key) + ': ' + reason)
        issues = []
        if key in RAW_V2[1:] and not isinstance(metadata.get('measurement_semantics'), str):
            issues.append('measurement_semantics_unverified')
        if key in ('windGust', 'windSpeed10Min') and metadata.get('measurement_window_seconds') is None:
            issues.append('measurement_window_unverified')
        entry = {'measurement': deepcopy(item), 'canonical_variable': key, 'included': reason is None,
                 'exclusion_reason': reason, 'expected_unit': UNITS_V2.get(key),
                 'unit_compatible': key in UNITS_V2 and item.get('unit') == UNITS_V2[key],
                 'age_seconds_at_query': measurement_age, 'report_age_seconds_at_query': report_age,
                 'semantic_issues': issues}
        if receipt is not None: entry['sensor_time_audit'] = {k: v for k, v in receipt.items() if k != 'reason'}
        readings.append(entry)
    route = deepcopy(context.get('route_sensor_audit', {}))
    for key, entry in route.get('variables', {}).items():
        if entry.get('exclusion_reason'): warnings.append(key + ': ' + entry['exclusion_reason'])
    used = sum(values[k] is not None for k in RAW_V2[1:])
    state = 'insufficient' if used == 0 else 'complete' if used == len(RAW_V2)-1 else 'partial'
    return {
        'schema_version': 'd-day-context-audit-v2', 'weather_model_version': INTENSITY,
        'values': values, 'warnings': sorted(set(warnings)), 'readings': readings,
        'route_coverage': route, 'weather_used': used, 'weather_expected': len(RAW_V2)-1,
        'scope': context.get('scope', 'unspecified'), 'actual_trip_exposure_reconstructed': False,
        'semantic_metadata_complete': bool(used) and all(not r['semantic_issues'] for r in readings if r['included']),
        'feature_columns': ['intercept', *FEATURES_V2], 'encoded_design_row': design_row_v2(values).tolist(),
        'context_state': state, 'contextual_prediction_allowed_for_research': used > 0,
        'fallback': 'basic' if used == 0 else None,
        'rain_intensity_used': values['rainIntensity'] is not None,
        'use_policy': dict(POLICY), 'production_activation_allowed': False,
    }
