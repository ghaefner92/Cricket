import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'app-backend/services/dyconet'))
from otp_transit import OtpTransitClient
c=OtpTransitClient()
q='''{ stops { gtfsId name lat lon code } }'''
r=c.request(c.base+'/otp/routers/default/index/graphql',{'query':q})
print(json.dumps({'errors':r.get('errors'),'count':len(r.get('data',{}).get('stops',[])),
                 'sample':r.get('data',{}).get('stops',[])[:3]},ensure_ascii=True))
