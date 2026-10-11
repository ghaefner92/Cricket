"""Offline contract review of an archived query; never supplies training evidence."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app-backend/services/dyconet'))
from orion_context import OrionEntity
from orion_query_list import OrionQueryListProvider
from comfort_pilot.context_capture import prepare_reading
from comfort_pilot.context_audit import audit_context
from comfort_pilot.sensor_time import attach_reception
from comfort_pilot.model import WEATHER, identity
from comfort_pilot.weather_contract import INTENSITY, specification


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    raw = json.loads(args.archive.read_text(encoding='utf-8'))
    if raw['http_status'] != 200: raise ValueError('Archive is not a successful Orion query')
    query_received = raw['received_at']
    provider = OrionQueryListProvider()
    provider._weather_list = tuple(OrionEntity.from_mapping(e) for e in raw['payload'])
    stations = []
    for entity in provider._weather_list:
        variables = WEATHER + (('cumulativeRainfall',) if entity.entity_id == 'Sensor:Weather:Winfred' else ())
        derived = [prepare_reading(attach_reception(o.to_dict(), query_received))
                   for o in provider.observations(entity, variables=variables)]
        context = {'queried_at': query_received, 'weather_model_version': INTENSITY,
                   'scope': 'offline_review_of_archived_provider_query', 'measurements': derived}
        stations.append({'entity_id': entity.entity_id, 'derived_context': context,
                         'audit': audit_context(context, query_received)})
    report = {'schema_version': 'd-day-weather-contract-review-v2',
              'reviewed_at': datetime.now(timezone.utc).isoformat(), 'source_archive': str(args.archive),
              'source_archive_digest': identity(raw), 'source_response_received_at': query_received,
              'weather_contract': specification(), 'stations': stations,
              'historical_raw_rewritten': False, 'retrospective_contract_review': True,
              'eligible_as_new_training_event': False, 'new_live_query_performed': False,
              'active_passport_modified': False, 'scientific_validation_claimed': False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    print(json.dumps({'output': str(args.output), 'stations': [
        {'id': s['entity_id'], 'usable_weather': s['audit']['weather_used'],
         'rain_intensity_used': s['audit']['rain_intensity_used'], 'warnings': s['audit']['warnings']}
        for s in stations]}, ensure_ascii=False))


if __name__ == '__main__': main()
