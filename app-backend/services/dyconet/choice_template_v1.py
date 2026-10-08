"""Versioned deterministic companion explanation of choice evidence.

No inference about actual motives, emotions, completed goals or causal shares.
Statement refs are RFC6901 pointers into the phase-1 evidence document.
"""
from __future__ import annotations
import argparse
import copy
import json
import math
from pathlib import Path
from choice_evidence_v1 import SCHEMA as EVIDENCE_SCHEMA, digest, ChoiceEvidenceError

SCHEMA = 'cricket-choice-template-v1'
RULE_VERSION = 'choice-reflection-rules-1.0'
MODES = {
 'en': {'car':'the car','bike':'the bicycle','walk':'walking','pt':'public transport'},
 'de': {'car':'das Auto','bike':'das Fahrrad','walk':'das Gehen','pt':'öffentliche Verkehrsmittel'},
 'es': {'car':'el coche','bike':'la bicicleta','walk':'caminar','pt':'el transporte público'}}
NEEDS = {
 'en': ['reducing environmental impact','being physically active','having personal space','autonomy','keeping costs down','saving time','traffic safety','personal safety','comfort','reliability','reducing infection exposure'],
 'de': ['geringere Umweltbelastung','körperliche Aktivität','persönlichen Freiraum','Autonomie','geringe Kosten','Zeitersparnis','Verkehrssicherheit','persönliche Sicherheit','Komfort','Zuverlässigkeit','geringere Infektionsexposition'],
 'es': ['reducir el impacto ambiental','mantenerte activo','tener espacio personal','autonomía','cuidar el gasto','ahorrar tiempo','seguridad vial','seguridad personal','comodidad','fiabilidad','reducir la exposición a infecciones']}
NEED_KEYS = ['pro_env','physical','privacy','autonomy','cost','speed','safety_accident','safety_crime','comfort','reliable','health_infection']
LABELS = {lang: dict(zip(NEED_KEYS, values)) for lang,values in NEEDS.items()}


def finite(v):
 return isinstance(v,(int,float)) and not isinstance(v,bool) and math.isfinite(v)


def pointer(document, ref):
 value=document
 for part in ref.lstrip('/').split('/'):
  part=part.replace('~1','/').replace('~0','~')
  value=value[int(part)] if isinstance(value,list) else value[part]
 return value


def render_choice_template(evidence, *, language='en'):
 if language not in MODES: raise ChoiceEvidenceError('language must be en, de or es')
 if not isinstance(evidence,dict) or evidence.get('schema_version')!=EVIDENCE_SCHEMA:
  raise ChoiceEvidenceError('phase-1 evidence required')
 expected=digest({k:v for k,v in evidence.items() if k!='evidence_id'})
 if evidence.get('evidence_id')!=expected: raise ChoiceEvidenceError('evidence content hash mismatch')
 mode=evidence.get('identity',{}).get('model_mode')
 if mode not in MODES[language]: raise ChoiceEvidenceError('unsupported model mode')
 statements=[]
 def add(rule,section,text,refs):
  for ref in refs:
   try:pointer(evidence,ref)
   except (KeyError,IndexError,ValueError,TypeError) as exc:
    raise ChoiceEvidenceError('missing statement evidence: '+ref) from exc
  statements.append({'id':f's{len(statements)+1}','rule':rule,'section':section,'text':text,'evidence_refs':refs})
 def tr(en,de,es):return {'en':en,'de':de,'es':es}[language]
 name=MODES[language][mode]
 add('SAVED_CHOICE','selection',tr(f'You chose {name}.',f'Du hast {name} gewählt.',f'Elegiste {name}.'),
     ['/identity/model_mode','/identity/chosen_at','/identity/choice_event_id'])
 criteria={'choice_vs_simulation':'UNKNOWN','weekly_goals':'UNKNOWN','cognitive_affective_relation':'UNKNOWN','context_effect':'UNKNOWN'}
 if evidence.get('status')!='EVIDENCE_READY':
  add('NO_CONTEXT_SIMULATION','comparison',tr('There is no contextual simulation available for this saved choice.',
      'Für diese gespeicherte Auswahl liegt keine kontextuelle Simulation vor.',
      'No hay una simulación contextual disponible para esta elección guardada.'),['/status'])
 else:
  d=evidence['deliberation'];current=d['contextual'];leader=current.get('competition',{}).get('winner')
  rival=current.get('competition',{}).get('main_rival')
  supports=current.get('terminal_action_support',{})
  refs=['/deliberation/ambiguity_state','/deliberation/contextual/competition/winner',
        '/deliberation/contextual/terminal_action_support']
  ambiguous=d.get('ambiguity_state') in ('NEAR_TIE','UNRESOLVED')
  clear=d.get('ambiguity_state')=='CLEAR' and current.get('leadership',{}).get('final_leader_identifiable') is True
  if leader not in MODES[language] or not finite(supports.get(mode)) or not finite(supports.get(leader)):
   add('COMPARISON_UNKNOWN','comparison',tr('The available evidence does not allow a comparison with the simulated tendency.',
       'Die verfügbaren Daten erlauben keinen Vergleich mit der simulierten Tendenz.',
       'La evidencia disponible no permite comparar tu elección con la tendencia simulada.'),refs)
  elif clear:
   criteria['choice_vs_simulation']='ALIGNED_CLEAR' if mode==leader else 'DIFFERENT_CLEAR'
   refs.append('/deliberation/contextual/leadership/final_leader_identifiable')
   if mode==leader:
    add('ALIGNED_CLEAR','comparison',tr('Your choice matches the clearly differentiated tendency in this simulation.',
        'Deine Auswahl stimmt mit der klar unterscheidbaren Tendenz dieser Simulation überein.',
        'Tu elección coincide con la tendencia claramente diferenciada de esta simulación.'),refs)
   else:
    lead=MODES[language][leader]
    add('DIFFERENT_CLEAR','comparison',tr(f'Your choice differs from {lead}, which received the greatest simulated action activation.',
        f'Deine Auswahl weicht von der führenden Alternative ab: {lead}.',
        f'Tu elección difiere de {lead}, la alternativa con mayor activación simulada.'),refs)
  elif ambiguous and rival in MODES[language] and finite(supports.get(rival)):
   refs.append('/deliberation/contextual/competition/main_rival')
   a,b=MODES[language][leader],MODES[language][rival]
   add('AMBIGUOUS_LEADING_PAIR','comparison',tr(f'{a.capitalize()} and {b} remain close in the simulation; neither is clearly differentiated.',
       f'Die führenden Alternativen {a} und {b} liegen in der Simulation nahe beieinander.',
       f'{a.capitalize()} y {b} permanecen próximos en la simulación, sin una diferenciación clara.'),refs)
   if mode in (leader,rival):
    criteria['choice_vs_simulation']='WITHIN_AMBIGUOUS_LEADING_PAIR'
    add('CHOICE_IN_AMBIGUOUS_PAIR','comparison',tr('You chose one of those closely competing alternatives.',
        'Du hast eine dieser eng konkurrierenden Alternativen gewählt.',
        'Elegiste una de esas alternativas próximas.'),refs+['/identity/model_mode'])
   elif supports[mode]<min(supports[leader],supports[rival]):
    criteria['choice_vs_simulation']='LOWER_THAN_AMBIGUOUS_LEADING_PAIR'
    add('CHOICE_BELOW_AMBIGUOUS_PAIR','comparison',tr('Your chosen mode received lower action activation than both of those alternatives in this run.',
        'Dein gewähltes Verkehrsmittel erhielt in diesem Lauf eine geringere Aktivierung als beide Alternativen.',
        'El modo que elegiste recibió menor activación que ambas alternativas en esta ejecución.'),refs+['/identity/model_mode'])
  else:
   add('COMPARISON_UNKNOWN','comparison',tr('The available diagnostics do not establish a clearly differentiated tendency.',
       'Die verfügbaren Diagnosen zeigen keine klar unterscheidbare Tendenz.',
       'Los diagnósticos disponibles no establecen una tendencia claramente diferenciada.'),refs)

  weekly=evidence.get('weekly_goals',{})
  if weekly.get('status')=='AVAILABLE':
   points=weekly['points'];mapping=weekly['goal_to_need'];terms=evidence['selected_relations']['by_need']
   by_need={row['need']:(i,row) for i,row in enumerate(terms)}
   relevant=[]
   for goal,need in mapping.items():
    if points.get(goal,0)>0 and need in by_need:
     i,row=by_need[need]
     relevant.append((goal,need,points[goal],i,row))
   signs=set()
   # Keep the brief explanation to two priorities in total, representing
   # both support and opposition when both are present.
   priority_limit=1 if {'SUPPORT','OPPOSITION'}.issubset({r[4]['relation'] for r in relevant}) else 2
   for relation,section in [('OPPOSITION','tensions'),('SUPPORT','affinities')]:
    selected=[r for r in relevant if r[4]['relation']==relation and finite(r[4]['signed_input_terminal'])
              and (r[4]['signed_input_terminal']<0 if relation=='OPPOSITION' else r[4]['signed_input_terminal']>0)]
    selected.sort(key=lambda r:(-r[2],-abs(r[4]['signed_input_terminal']),r[1]))
    if not selected:continue
    signs.add(relation)
    selected=selected[:priority_limit];labels=', '.join(LABELS[language][r[1]] for r in selected)
    text=tr(f'The model represents tensions with these weekly priorities: {labels}.' if relation=='OPPOSITION' else f'The model represents support linked to these weekly priorities: {labels}.',
      f'Das Modell zeigt Spannungen mit diesen Wochenprioritäten: {labels}.' if relation=='OPPOSITION' else f'Das Modell zeigt Unterstützung im Zusammenhang mit diesen Wochenprioritäten: {labels}.',
      f'El modelo representa tensiones con estas prioridades semanales: {labels}.' if relation=='OPPOSITION' else f'El modelo representa apoyos relacionados con estas prioridades semanales: {labels}.')
    refs=[]
    for goal,need,priority,i,row in selected:
     refs.extend([f'/weekly_goals/points/{goal}',f'/weekly_goals/goal_to_need/{goal}',
                  f'/selected_relations/by_need/{i}/relation',f'/selected_relations/by_need/{i}/signed_input_terminal',
                  f'/selected_relations/by_need/{i}/belief_source'])
    add('GOALS_'+relation,section,text,refs)
   criteria['weekly_goals']='MIXED' if len(signs)==2 else 'OPPOSITION_PRESENT' if 'OPPOSITION'in signs else 'SUPPORT_PRESENT' if signs else 'NO_SIGNED_RELATIONS'
  else:
   add('GOALS_UNKNOWN','uncertainty',tr('The original weekly allocation is unavailable, so I cannot assess its relation to your choice.',
       'Die ursprüngliche Wochenverteilung fehlt; ihre Beziehung zu deiner Auswahl kann ich nicht beurteilen.',
       'Falta la distribución semanal original, por lo que no puedo evaluar su relación con tu elección.'),['/weekly_goals/status'])

  support=evidence['selected_relations']['support_dynamics']
  cognitive=support.get('cognitive_signed_input',{}).get('terminal')
  affective=support.get('affective_signed_input',{}).get('terminal')
  refs=['/selected_relations/support_dynamics/cognitive_signed_input/terminal',
        '/selected_relations/support_dynamics/affective_signed_input/terminal']
  if finite(cognitive) and finite(affective):
   if cognitive*affective<0:
    criteria['cognitive_affective_relation']='OPPOSED_CHANNELS'
    add('OPPOSED_CHANNELS','model_detail',tr('The cognitive and affective signed inputs point in opposite directions for your chosen mode.',
        'Kognitive und affektive Eingänge weisen für dein gewähltes Verkehrsmittel in entgegengesetzte Richtungen.',
        'Las entradas cognitivas y afectivas apuntan en direcciones opuestas para el modo elegido.'),refs)
   elif cognitive<0 and affective<0:
    criteria['cognitive_affective_relation']='JOINT_OPPOSITION'
    add('JOINT_OPPOSITION','model_detail',tr('Both channels oppose this mode at the terminal state; this is not opposition between the channels.',
        'Beide Kanäle sprechen im Endzustand gegen dieses Verkehrsmittel; sie stehen dabei nicht im Gegensatz zueinander.',
        'Ambos canales aportan oposición a este modo en el estado final; no se oponen entre sí.'),refs)
   elif cognitive>0 and affective>0:
    criteria['cognitive_affective_relation']='JOINT_SUPPORT'
    add('JOINT_SUPPORT','model_detail',tr('Both channels support this mode at the terminal state.',
        'Beide Kanäle unterstützen dieses Verkehrsmittel im Endzustand.',
        'Ambos canales aportan apoyo a este modo en el estado final.'),refs)
   else:criteria['cognitive_affective_relation']='ZERO_CHANNEL'

  delta=d.get('selected_activation_delta')
  if finite(delta):
   refs=['/deliberation/selected_activation_delta','/deliberation/baseline','/deliberation/contextual']
   if d['baseline']==d['contextual']:
    criteria['context_effect']='NO_CHANGE_IN_EXPORTED_DIAGNOSTICS'
    add('CONTEXT_NO_CHANGE','context',tr('The exported simulation results are unchanged after incorporating the available context.',
        'Die exportierten Simulationsergebnisse bleiben nach Einbezug des verfügbaren Kontexts unverändert.',
        'Los resultados exportados de la simulación no cambiaron al incorporar el contexto disponible.'),refs)
   else:
    criteria['context_effect']='SELECTED_ACTIVATION_INCREASED' if delta>0 else 'SELECTED_ACTIVATION_DECREASED' if delta<0 else 'CHANGED_ELSEWHERE'
    text=tr('The chosen mode’s action activation increased with the contextual run.' if delta>0 else 'The chosen mode’s action activation decreased with the contextual run.' if delta<0 else 'Other exported diagnostics changed, while the chosen mode’s terminal activation stayed unchanged.',
        'Die Aktivierung des gewählten Verkehrsmittels stieg im kontextuellen Lauf.' if delta>0 else 'Die Aktivierung des gewählten Verkehrsmittels sank im kontextuellen Lauf.' if delta<0 else 'Andere exportierte Diagnosen änderten sich, die Endaktivierung des gewählten Verkehrsmittels jedoch nicht.',
        'La activación del modo elegido aumentó en la ejecución contextual.' if delta>0 else 'La activación del modo elegido disminuyó en la ejecución contextual.' if delta<0 else 'Otros diagnósticos exportados cambiaron, mientras la activación final del modo elegido se mantuvo igual.')
    add('CONTEXT_CHANGE','context',text,refs)
  context=evidence.get('context') or {}
  missing=context.get('perturbation',{}).get('missing_stressors',[])
  if missing:
   add('CONTEXT_MISSING','uncertainty',tr('Some contextual dimensions are unknown and cannot be treated as favourable conditions.',
       'Einige Kontextdimensionen sind unbekannt und dürfen nicht als günstige Bedingungen gelten.',
       'Algunas dimensiones contextuales son desconocidas y no pueden tratarse como condiciones favorables.'),['/context/perturbation/missing_stressors'])
  observations=context.get('raw_observations',[])
  if any(o.get('status')=='OBSERVED' and o.get('timestamp') is None for o in observations):
   add('OBSERVATION_TIME_UNKNOWN','model_detail',tr('Some observations have no measurement timestamp; their freshness is unknown.',
       'Einige Beobachtungen haben keinen Messzeitpunkt; ihre Aktualität ist unbekannt.',
       'Algunas observaciones carecen de fecha de medición; su actualidad es desconocida.'),['/context/raw_observations'])
  if (evidence.get('geometry_evidence') or {}).get('route_identity_verified') is False:
   add('GEOMETRY_UNVERIFIED','model_detail',tr('The contextual path is supplemental; its identity with the externally ranked route is unverified.',
       'Der Kontextpfad ist ergänzend; seine Identität mit der extern bewerteten Route ist nicht bestätigt.',
       'El trayecto contextual es complementario; su identidad con la ruta ordenada externamente no está verificada.'),['/geometry_evidence/route_identity_verified'])

 add('INTERPRETATION_LIMIT','model_detail',tr('This explains simulated relations, including estimated ones; it does not establish your motives, experienced conflict or completed goals.',
     'Dies erklärt simulierte, teilweise geschätzte Beziehungen; es belegt weder deine Motive noch erlebten Konflikt oder erreichte Ziele.',
     'Esto explica relaciones simuladas, algunas estimadas; no establece tus motivos, conflicto experimentado ni metas cumplidas.'),['/limitations'])
 output={'schema_version':SCHEMA,'rule_version':RULE_VERSION,'language':language,
         'source_evidence_id':evidence['evidence_id'],'choice_event_id':evidence['identity']['choice_event_id'],
         'generated_by_llm':False,'criteria':criteria,'statements':statements,
         'message':' '.join(s['text'] for s in statements if s['section'] in ('selection','comparison','tensions','affinities','context'))}
 output['template_id']=digest(output)
 return copy.deepcopy(output)


def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--evidence',type=Path,required=True)
 parser.add_argument('--language',choices=['en','de','es'],default='en')
 parser.add_argument('--output',type=Path,required=True)
 args=parser.parse_args()
 evidence=json.loads(args.evidence.read_text(encoding='utf-8-sig'))
 result=render_choice_template(evidence,language=args.language)
 with args.output.open('x',encoding='utf-8') as stream:
  json.dump(result,stream,ensure_ascii=False,allow_nan=False,indent=2)
 print(result['message'])
 print('Template saved:',args.output)

if __name__=='__main__':main()
