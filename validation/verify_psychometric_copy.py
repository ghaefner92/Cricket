"""Audit copy-only scope, unchanged scales/artwork, and historical voice catalogs.

No requests to an LLM, no phone writes, and no synthetic experiences stored.
"""
import ast
import json
import pathlib
import re
import sys

ROOT=pathlib.Path(__file__).resolve().parents[1]
BASE=ROOT/'.local-backups/psychometric-copy-0469'
OUTPUT=ROOT/'output/psychometric-copy-0469'
OUTPUT.mkdir(parents=True,exist_ok=True)
STRING=re.compile(r'"(?:\\.|[^"\\])*"')
checks=[]
diffs=[]

class HideStrings(ast.NodeTransformer):
    def visit_Constant(self,node):
        if isinstance(node.value,str): node.value='<string>'
        return node

for backup in BASE.rglob('*'):
    if not backup.is_file() or backup.name=='changes.json': continue
    relative=backup.relative_to(BASE)
    current=ROOT/relative
    before=backup.read_text(encoding='utf-8')
    after=current.read_text(encoding='utf-8')
    if current.suffix=='.kt' or current.name=='build.gradle.kts':
        if current.name=='build.gradle.kts': before=before.replace('versionCode = 79','versionCode = 80')
        assert STRING.sub('<string>',before)==STRING.sub('<string>',after), f'Non-text Kotlin change: {relative}'
        old=STRING.findall(before);new=STRING.findall(after)
        assert len(old)==len(new)
        diffs.extend({'file':str(relative),'before':a,'after':b} for a,b in zip(old,new) if a!=b)
        checks.append(f'{relative}: only string literals changed (release versionCode excepted)')
    elif current.suffix=='.py':
        a=ast.dump(HideStrings().visit(ast.parse(before)),include_attributes=False)
        b=ast.dump(HideStrings().visit(ast.parse(after)),include_attributes=False)
        assert a==b, f'Non-text Python change: {relative}'
        checks.append(f'{relative}: executable AST unchanged except text')

native=ROOT/'android-native/app/src/main/assets'
old=json.loads((BASE/'android-native/app/src/main/assets/catalog.json').read_text(encoding='utf-8'))
new=json.loads((native/'catalog.json').read_text(encoding='utf-8'))
for section in ['goalArt','icons','backdrops']: assert new[section]==old[section]
for lang in ['en','de']:
    assert list(new['weekly'][lang]['goals'])==list(old['weekly'][lang]['goals'])
    assert list(new['beliefs'][lang]['needs'])==list(old['beliefs'][lang]['needs'])
    assert new['beliefs'][lang]['labels']==old['beliefs'][lang]['labels']
    for key in new['weekly'][lang]:
        if key!='goals': assert new['weekly'][lang][key]==old['weekly'][lang][key]
checks.append('All artwork, goal/need keys, bipolar belief anchors and legacy allocation instructions unchanged')

oldvoice=json.loads((BASE/'app-backend/services/dyconet/choice_wording_v1.json').read_text(encoding='utf-8'))
voice=json.loads((ROOT/'app-backend/services/dyconet/choice_wording_v1.json').read_text(encoding='utf-8'))
assert voice==json.loads((native/'choiceWordings.json').read_text(encoding='utf-8'))
assert voice['legacy']['choice-voice-1.3']=={k:v for k,v in oldvoice.items() if k!='legacy'}
assert all(voice['legacy'][k]==v for k,v in oldvoice['legacy'].items())
for lang in ['en','de']:
    assert [{k:v for k,v in q.items() if k!='text'} for q in voice['questions'][lang]]==[{k:v for k,v in q.items() if k!='text'} for q in oldvoice['questions'][lang]]
assert voice['tones']==oldvoice['tones']
checks.append('Choice voice 1.4 synchronized; all legacy text, reflection IDs/eligibility and tones preserved')

sys.path.insert(0,str(ROOT/'app-backend/services/dyconet'))
from choice_groq_narrator import catalog, narrate_choice, validate_selection
from choice_evidence_v1 import digest
from choice_story_v1 import render_choice_story

# Exercise bilingual alternatives, fallback and rejection using a minimal
# read-only template. These reports never enter the app's records or learning.
for lang in ['en','de']:
    template={'schema_version':'cricket-choice-template-v1','language':lang,
              'source_evidence_id':'scope-check-only','choice_event_id':'scope-check-only',
              'criteria':{},'statements':[]}
    for i,rule in enumerate(['GOALS_SUPPORT','GOALS_OPPOSITION','INTERPRETATION_LIMIT']):
        template['statements'].append({'id':str(i),'rule':rule,'section':'affinities' if i==0 else 'tensions',
             'text':voice['replacements'][lang][rule][0]+'X' if i<2 else voice['replacements'][lang][rule][0],
             'evidence_refs':[]})
    template['template_id']=digest(template)
    prompt=catalog(template)
    assert all(len(s['options'])==2 for s in prompt['statements'])
    chosen={'opening':prompt['opening_indices'][0],'tone':prompt['tone'],'question':'none',
            'emphasis':'affinities','variants':{s['id']:1 for s in prompt['statements']}}
    result=narrate_choice(template,provider=lambda _:chosen)
    assert result['status']=='AVAILABLE' and result['wording_version']=='choice-voice-1.4'
    failed=narrate_choice(template,provider=lambda _:dict(chosen,variants={}))
    assert failed['status']=='TEMPLATE_FALLBACK' and failed['generated_by_llm'] is False
checks.append('EN/DE narration accepts reviewed text variants and rejects missing statement coverage')

# Inventory all localized text pairs in native screens, including unchanged
# operational instructions, for the full audit rather than only edited prompts.
pair=re.compile(r'(?:model\.text|text)\(\s*("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")')
inventory=[]
for path in sorted((ROOT/'android-native/app/src/main/java/de/ovgu/imiq/cricket').glob('*.kt')):
    text=path.read_text(encoding='utf-8')
    for match in pair.finditer(text):
        inventory.append({'file':str(path.relative_to(ROOT)),'line':text.count('\n',0,match.start())+1,
                          'en':match.group(1),'de':match.group(2)})
report={'copy_revision':'psychometric-copy-1.0','app_version':'0.4.69','checks':checks,
        'changed_native_string_literals':len(diffs),'native_text_pairs_in_inventory':len(inventory),
        'scientific_validation':False,'responses_rewritten':False,'aesthetic_changes':False}
(OUTPUT/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
(OUTPUT/'native-changes.json').write_text(json.dumps(diffs,ensure_ascii=False,indent=2),encoding='utf-8')
(OUTPUT/'native-text-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='checks'},ensure_ascii=True))
