"""Separate individual affective posterior, raw -3..3, normalized raw/3."""
import argparse,json
from pathlib import Path
from flask import request,jsonify
from .api import analyze
from .evidence import request_from_backup

def analyze_affect(data):return analyze(data,target="affect")

def register_affect(app):
    @app.post('/api/dyconet/d-day/affect-pilot/analyze')
    def endpoint():
        if request.content_length is not None and request.content_length>4*1024*1024:return jsonify(error='pilot_request_too_large'),413
        try:
            data=request.get_json(silent=True)
            if not isinstance(data,dict) or len(data.get('observations',[]))>512 or not 1<=len(data.get('prediction_contexts',[]))<=8:raise ValueError('Use offline analysis for larger data')
            return jsonify(analyze_affect(data))
        except (ValueError,KeyError,TypeError,IndexError,AttributeError,OverflowError):
            return jsonify(error='invalid_affect_pilot_request',message='Invalid affective evidence, normalization, context or sampler settings'),422

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('input',type=Path);parser.add_argument('--output',required=True,type=Path)
    parser.add_argument('--backup',action='store_true');parser.add_argument('--contexts',type=Path);parser.add_argument('--exclusions',type=Path)
    from .weather_contract import LEGACY, INTENSITY
    parser.add_argument('--weather-model-version', choices=(LEGACY, INTENSITY));args=parser.parse_args()
    data=json.loads(args.input.read_text(encoding='utf-8'));excluded=[]
    if args.backup:
        if not args.contexts:parser.error('--backup requires --contexts')
        exclusions=json.loads(args.exclusions.read_text(encoding='utf-8')) if args.exclusions else None
        data,excluded=request_from_backup(data,prediction_contexts=json.loads(args.contexts.read_text(encoding='utf-8')),target='affect',exclusions=exclusions,weather_model_version=args.weather_model_version)
    elif args.exclusions:parser.error('--exclusions requires --backup')
    if args.weather_model_version:
        if data.get('weather_model_version',args.weather_model_version)!=args.weather_model_version:parser.error('Input and command weather model versions differ')
        data['weather_model_version']=args.weather_model_version
    result=analyze_affect(data);result['excluded_legacy_events']=excluded
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8')
    print(json.dumps({'status':'complete','learning_target':'affect','active_passport_modified':False}))

if __name__=='__main__':main()
