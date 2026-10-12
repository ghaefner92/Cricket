"""Diagnose provider status without printing credentials or response headers."""
import json,sys
from pathlib import Path
from dotenv import load_dotenv
import os,httpx
ROOT=Path(__file__).resolve().parents[1]
load_dotenv(ROOT/'app-backend/.env')
key=os.getenv('GROQ_API_KEY')
if not key:raise SystemExit('Configured Groq key is missing')
with httpx.Client(timeout=25) as c:
 r=c.get('https://api.groq.com/openai/v1/models',headers={'Authorization':'Bearer '+key})
 try:d=r.json()
 except ValueError:d={}
 error=d.get('error',{}) if isinstance(d,dict) else {}
 if not isinstance(error,dict):error={}
 # Groq errors are projected to known metadata; never print the full HTTP body.
 message=str(error.get('message',''))
 clean={'status':r.status_code,'content_type':r.headers.get('content-type',''),
  'error_type':error.get('type'),'error_code':error.get('code'),
  'message_flags':{w:w in message.lower() for w in ['permission','country','denied','blocked','organization','key']},
  'model_available':any(x.get('id')=='openai/gpt-oss-120b' for x in d.get('data',[])) if isinstance(d,dict) else False}
 if r.status_code!=200:
  clean['safe_message']=message.replace(key,'[redacted]')[:400]
 print(json.dumps(clean))
