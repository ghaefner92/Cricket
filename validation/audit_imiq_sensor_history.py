"""Bounded read-only public Orion/QuantumLeap audit. Does not alter providers."""
from datetime import datetime, timezone
import json
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlencode, quote
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output/imiq-sensor-research-2026-10-11" / datetime.now(timezone.utc).strftime("sample-%H%M%S")
OUT.mkdir(parents=True, exist_ok=True)
BASE = "https://imiq-public.et.uni-magdeburg.de/api"


def fetch(name, url):
    started = datetime.now(timezone.utc).isoformat()
    try:
        with urlopen(Request(url, headers={"User-Agent":"Cricket-readonly-contract-audit","Accept":"application/json"}), timeout=25) as response:
            body = response.read(3_000_001)
            if len(body)>3_000_000:
                raise ValueError("Public response exceeds audit size cap")
            payload = json.loads(body)
            result = {"http_status":response.status,"url":url,"requested_at":started,
                      "received_at":datetime.now(timezone.utc).isoformat(),"payload":payload}
    except HTTPError as e:
        result={"http_status":e.code,"url":url,"requested_at":started,"error":"HTTP error"}
    except (URLError,ValueError) as e:
        result={"http_status":None,"url":url,"requested_at":started,"error":str(e)}
    (OUT / (name+".json")).write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    return result


def fetch_text(name,url):
    try:
        with urlopen(Request(url,headers={"User-Agent":"Cricket-readonly-contract-audit"}),timeout=25) as response:
            text=response.read(2_000_001).decode("utf-8")
        (OUT/(name+".txt")).write_text(text,encoding="utf-8")
        print(json.dumps({"source_saved":name,"url":url}))
    except (HTTPError,URLError,ValueError) as e:
        print(json.dumps({"source_failed":name,"error":str(e)}))


org = fetch("github-repositories","https://api.github.com/orgs/imiq-project/repos?per_page=100")
print(json.dumps({"repositories":[{"name":r["name"],"description":r.get("description"),"default_branch":r["default_branch"]} for r in org.get("payload",[]) ]},ensure_ascii=True))
summary=[]
weather = fetch("weather-query-list",BASE+"/orion/entities?type=Weather&limit=100&metadata=dateModified,dateCreated")
tree = fetch("spacetime-tree","https://api.github.com/repos/imiq-project/SpaceTime/git/trees/main?recursive=1")
print(json.dumps({"spacetime_files":[x["path"] for x in tree.get("payload",{}).get("tree",[]) if x.get("type")=="blob"]}))
for path in ("README.md","spacetime/util.py","spacetime/continuous.py","spacetime/main.py"):
    fetch_text("SpaceTime-"+path.replace("/","-"),"https://raw.githubusercontent.com/imiq-project/SpaceTime/"+tree.get("payload",{}).get("sha","main")+"/"+path)
decoder_tree=fetch("seeed-decoder-tree","https://api.github.com/repos/Seeed-Solution/SenseCAP-Decoder/git/trees/main?recursive=1")
print(json.dumps({"seeed_decoder_files":[x["path"] for x in decoder_tree.get("payload",{}).get("tree",[]) if "2120" in x["path"].lower()]}))
fetch_text("Seeed-S2120-TTN-decoder","https://raw.githubusercontent.com/Seeed-Solution/SenseCAP-Decoder/"+decoder_tree.get("payload",{}).get("sha","main")+"/S2120/TTN/SenseCAP_S2120_TTN_Decoder.js")
for station in ("Walter","Winfred"):
    entity="Sensor:Weather:"+station
    current={"http_status":weather["http_status"],"payload":next((e for e in weather.get("payload",[]) if e.get("id")==entity),{})}
    historical=fetch(station+"-history",BASE+"/quantumleap/entities/"+quote(entity,safe="")+"?"+urlencode({"lastN":24}))
    c=current.get("payload",{});h=historical.get("payload",{})
    if not isinstance(h,dict):h={}
    summary.append({"station":station,"current_http":current["http_status"],"historical_http":historical["http_status"],
        "current_attribute_names":sorted(c) if isinstance(c,dict) else [],
        "current_values":{k:v for k,v in c.items() if k in ("datetime","dateModified","rain","rainHourly","rainDaily","rainIntensity","cumulativeRainfall","lightIntensity","uplinkInterval","temperature","windGust","peakWindGust","softwareVersion","hardwareVersion")},
        "historical_timestamps":h.get("index",[]),
        "historical_attributes":[{"name":a.get("attrName"),"count":len(a.get("values",[])),
             "nonnull":sum(v is not None for v in a.get("values",[])),"distinct":len({json.dumps(v,sort_keys=True) for v in a.get("values",[]) if v is not None}),
             "first":a.get("values",[None])[0] if a.get("values") else None,"last":a.get("values",[None])[-1] if a.get("values") else None} for a in h.get("attributes",[])]})
report={"audited_at":datetime.now(timezone.utc).isoformat(),"read_only":True,"station_history_row_limit":24,"stations":summary}
checks=[]
for station,attribute in (("Walter","rainHourly"),("Walter","rain"),("Winfred","rainIntensity"),("Winfred","cumulativeRainfall")):
    url=BASE+"/quantumleap/entities/"+quote("Sensor:Weather:"+station,safe="")+"/attrs/"+attribute+"?lastN=12"
    result=fetch(station+"-history-"+attribute,url)
    checks.append({"station":station,"attribute":attribute,"http_status":result["http_status"],"payload":result.get("payload")})
report["attribute_history_checks"]=checks
(OUT/"summary.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(json.dumps({"output":str(OUT),"stations":[{"station":s["station"],"current_http":s["current_http"],"historical_http":s["historical_http"],"rows":len(s["historical_timestamps"]),"attributes_with_values":[a["name"] for a in s["historical_attributes"] if a["nonnull"]]} for s in summary],"attribute_history_checks":checks},ensure_ascii=True,indent=2))
