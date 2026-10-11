"""Repeatable read-only audit of the public Weather QueryList, without participants."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from orion_query_list import OrionQueryListProvider
from .context_capture import prepare_reading
from .context_audit import audit_context
from .model import WEATHER, identity
from .sensor_time import attach_reception


def run(output, provider=None):
    provider = provider or OrionQueryListProvider()
    entities = provider.query_entities(entity_type="Weather",limit=100).entities
    if len(entities) >= 100: raise ValueError("QueryList may be truncated")
    # One query freezes both entities and their provider-confirmed shared clock.
    provider._weather_list = entities
    queried = datetime.now(timezone.utc).isoformat()
    stations = []
    for entity in entities:
        raw = [attach_reception(o.to_dict(), queried) for o in provider.observations(entity,variables=WEATHER)]
        context = {"queried_at":queried,"scope":"live_station_contract_audit",
                   "measurements":[prepare_reading(r) for r in raw]}
        stations.append({"entity_id":entity.entity_id,"entity_attribute_names":sorted(entity.attributes),
                         "raw_observations":raw,"context":context,"audit":audit_context(context,queried)})
    result = {"schema_version":"d-day-live-context-audit-v1","queried_at":queried,
              "stations":stations,"query_digest":identity(stations),"live_query_performed":True,
              "timezone_contract":"Europe/Berlin confirmed by author",
              "shared_timestamp_contract":"Walter datetime also dates Winfred; provider guarantee confirmed by author",
              "scientific_validation_claimed":False}
    output = Path(output);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2,allow_nan=False)+"\n",encoding="utf-8")
    return result


if __name__ == "__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--output",required=True,type=Path)
    r=run(p.parse_args().output)
    print(json.dumps({"stations":[{"id":s["entity_id"],"usable_weather":s["audit"]["weather_used"],
        "warnings":s["audit"]["warnings"]} for s in r["stations"]]},ensure_ascii=False))
