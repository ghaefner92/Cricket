"""Plain-language reflection from saved facts; full paths are not reconstructed."""
from __future__ import annotations
import copy
from choice_template_v1 import render_choice_template, finite, pointer, MODES
from choice_evidence_v1 import digest, ChoiceEvidenceError

VERSION = 'choice-story-1.0'


def render_choice_story(evidence, *, language='en'):
    if language not in ('en', 'de'):
        raise ChoiceEvidenceError('app language required')
    base = render_choice_template(evidence, language=language)
    story = copy.deepcopy(base)
    story['presentation_version'] = VERSION
    story['rule_version'] = 'choice-reflection-rules-1.1'
    de = language == 'de'
    def tr(en, german): return german if de else en
    replacements = {
        'ALIGNED_CLEAR': tr('Your choice matches the option that stands out clearly in this simulation.', 'Deine Wahl passt zu der Alternative, die in dieser Simulation klar hervorsticht.'),
        'CHOICE_IN_AMBIGUOUS_PAIR': tr('Your choice is one of these closely matched alternatives.', 'Deine Wahl gehört zu diesen ähnlich bewerteten Alternativen.'),
        'CHOICE_BELOW_AMBIGUOUS_PAIR': tr('The simulation favours both of those alternatives over your chosen mode.', 'Die Simulation spricht stärker für beide Alternativen als für dein gewähltes Verkehrsmittel.'),
        'COMPARISON_UNKNOWN': tr('The saved results do not show a clear tendency to compare with your choice.', 'Die gespeicherten Ergebnisse zeigen keine klare Tendenz für einen Vergleich mit deiner Wahl.'),
        'JOINT_SUPPORT': tr('At the end of the simulation, both the balance of needs and beliefs and the emotional evaluation support your choice.', 'Am Ende der Simulation sprechen sowohl die Abwägung von Bedürfnissen und Überzeugungen als auch die emotionale Bewertung für deine Wahl.'),
        'JOINT_OPPOSITION': tr('At the end of the simulation, both the balance of needs and beliefs and the emotional evaluation weigh against your choice.', 'Am Ende der Simulation sprechen sowohl die Abwägung von Bedürfnissen und Überzeugungen als auch die emotionale Bewertung gegen deine Wahl.'),
        'CONTEXT_NO_CHANGE': tr('The available environmental data did not change the saved simulation results.', 'Die verfügbaren Umweltdaten haben die gespeicherten Simulationsergebnisse nicht verändert.'),
        'OBSERVATION_TIME_UNKNOWN': tr('Some readings have no measurement time, so their freshness is unknown.', 'Bei einigen Messwerten fehlt der Messzeitpunkt; ihre Aktualität ist unbekannt.'),
        'GEOMETRY_UNVERIFIED': tr('The environmental information is associated with a supplementary path; its exact match to the chosen route has not been verified.', 'Die Umweltinformationen beziehen sich auf einen ergänzenden Verlauf; die genaue Übereinstimmung mit der gewählten Route ist nicht bestätigt.'),
        'INTERPRETATION_LIMIT': tr('This describes the saved simulation, not how you actually felt or whether you completed the journey or your goals.', 'Dies beschreibt die gespeicherte Simulation, nicht deine tatsächlichen Gefühle oder ob du den Weg oder deine Ziele abgeschlossen hast.')}
    mode = evidence['identity']['model_mode']
    d = evidence.get('deliberation') or {}
    support = (evidence.get('selected_relations') or {}).get('support_dynamics') or {}
    c = support.get('cognitive_signed_input') or {}
    a = support.get('affective_signed_input') or {}
    for row in story['statements']:
        if row['rule'] in replacements: row['text'] = replacements[row['rule']]
        if row['rule'] in ('JOINT_SUPPORT', 'JOINT_OPPOSITION', 'OPPOSED_CHANNELS'):
            row['section'] = 'balance'
        if row['rule'] == 'OPPOSED_CHANNELS':
            row['text'] = tr('At the end of the simulation, the balance of needs and beliefs supports your choice, while the emotional evaluation weighs against it.', 'Am Ende der Simulation spricht die Abwägung von Bedürfnissen und Überzeugungen für deine Wahl, die emotionale Bewertung dagegen.') if c['terminal'] > 0 else tr('At the end of the simulation, the emotional evaluation supports your choice, while the balance of needs and beliefs weighs against it.', 'Am Ende der Simulation spricht die emotionale Bewertung für deine Wahl, die Abwägung von Bedürfnissen und Überzeugungen dagegen.')
        if row['rule'] == 'AMBIGUOUS_LEADING_PAIR':
            comp = d['contextual']['competition']
            first, second = MODES[language][comp['winner']], MODES[language][comp['main_rival']]
            row['text'] = tr(f'{first.capitalize()} and {second} are closely matched in this simulation, so neither stands out clearly.', f'{first} und {second} liegen in dieser Simulation nahe beieinander; keine Alternative hebt sich klar ab.')
        if row['rule'] == 'DIFFERENT_CLEAR':
            leader = MODES[language][d['contextual']['competition']['winner']]
            row['text'] = tr(f'You chose a different option from the one that stands out in this simulation: {leader}.', f'Deine Wahl weicht von der Alternative ab, die in dieser Simulation hervorsticht: {leader}.')
        if row['rule'] in ('GOALS_SUPPORT','GOALS_OPPOSITION'):
            prefix, labels = row['text'].split(': ', 1)
            row['text'] = tr('In this simulation, your choice supports these weekly priorities: ', 'In dieser Simulation unterstützt deine Wahl diese Wochenprioritäten: ') + labels if row['rule'] == 'GOALS_SUPPORT' else tr('In this simulation, your choice is in tension with these weekly priorities: ', 'In dieser Simulation steht deine Wahl in Spannung zu diesen Wochenprioritäten: ') + labels
        if row['rule'] == 'CONTEXT_CHANGE':
            delta=d.get('selected_activation_delta')
            row['text']=tr('The available environmental data strengthened the simulated tendency toward your chosen mode.' if delta>0 else 'The available environmental data weakened the simulated tendency toward your chosen mode.' if delta<0 else 'The available environmental data changed other saved results, while the final tendency toward your chosen mode stayed the same.', 'Die verfügbaren Umweltdaten haben die simulierte Tendenz zu deinem gewählten Verkehrsmittel verstärkt.' if delta>0 else 'Die verfügbaren Umweltdaten haben die simulierte Tendenz zu deinem gewählten Verkehrsmittel abgeschwächt.' if delta<0 else 'Die verfügbaren Umweltdaten haben andere gespeicherte Ergebnisse verändert; die abschließende Tendenz zu deinem gewählten Verkehrsmittel blieb gleich.')
    def add(rule, section, text, refs):
        for ref in refs: pointer(evidence,ref)
        story['statements'].append({'id':f's{len(story["statements"])+1}','rule':rule,'section':section,'text':text,'evidence_refs':refs})
    if evidence.get('status') == 'EVIDENCE_READY':
        if story['criteria']['cognitive_affective_relation'] == 'ZERO_CHANNEL':
            add('BALANCE_ZERO','balance',tr('At the end of the simulation, one or both assessments contribute neither support nor resistance to your choice.', 'Am Ende der Simulation trägt mindestens eine der beiden Bewertungen weder Unterstützung noch Widerstand für deine Wahl bei.'),['/selected_relations/support_dynamics/cognitive_signed_input/terminal','/selected_relations/support_dynamics/affective_signed_input/terminal'])
        elif story['criteria']['cognitive_affective_relation'] == 'UNKNOWN':
            add('BALANCE_UNKNOWN','balance',tr('The saved results are insufficient to compare the balance of needs and beliefs with the emotional evaluation.', 'Die gespeicherten Ergebnisse reichen nicht aus, um die Abwägung von Bedürfnissen und Überzeugungen mit der affektiven Bewertung zu vergleichen.'),['/selected_relations/support_dynamics'])
        # Compare endpoints only. These differences are NOT slopes, monotonicity or reaction times.
        for channel, values, label, german_label in [('cognitive_signed_input',c,'balance of needs and beliefs','Abwägung von Bedürfnissen und Überzeugungen'),('affective_signed_input',a,'emotional evaluation','emotionale Bewertung')]:
            initial, terminal = values.get('initial'), values.get('terminal')
            if not (finite(initial) and finite(terminal)):continue
            if initial == terminal: continue
            ref=f'/selected_relations/support_dynamics/{channel}'
            if initial*terminal < 0:
                text=tr(f'From the start to the end of the simulation, the {label} changed from resistance to support for your choice.' if terminal>0 else f'From the start to the end of the simulation, the {label} changed from support to resistance for your choice.', f'Zwischen Beginn und Ende der Simulation wechselte die {german_label} von Widerstand zu Unterstützung für deine Wahl.' if terminal>0 else f'Zwischen Beginn und Ende der Simulation wechselte die {german_label} von Unterstützung zu Widerstand für deine Wahl.')
                rule='ENDPOINT_SIGN_CHANGE'
            elif initial>0 and terminal>0:
                text=tr(f'The {label} supported your choice at both the start and the end; this support was stronger at the end.' if terminal>initial else f'The {label} supported your choice at both the start and the end; this support was weaker at the end.', f'Die {german_label} unterstützte deine Wahl zu Beginn und am Ende; die Unterstützung war am Ende stärker.' if terminal>initial else f'Die {german_label} unterstützte deine Wahl zu Beginn und am Ende; die Unterstützung war am Ende schwächer.')
                rule='ENDPOINT_SUPPORT_CHANGE'
            elif initial<0 and terminal<0:
                text=tr(f'The {label} weighed against your choice at both the start and the end; this resistance was weaker at the end.' if terminal>initial else f'The {label} weighed against your choice at both the start and the end; this resistance was stronger at the end.', f'Die {german_label} sprach zu Beginn und am Ende gegen deine Wahl; der Widerstand war am Ende schwächer.' if terminal>initial else f'Die {german_label} sprach zu Beginn und am Ende gegen deine Wahl; der Widerstand war am Ende stärker.')
                rule='ENDPOINT_RESISTANCE_CHANGE'
            else: continue # Exact zero endpoint: no assumption about intermediate path.
            add(rule,'evolution',text,[ref+'/initial',ref+'/terminal'])
        friction=support.get('cognitive_affective_friction') or {}
        if finite(friction.get('mean_post_emergence')) and friction['mean_post_emergence']>0:
            add('OPPOSITION_DURING_WINDOW','evolution',tr('During at least part of the saved analysis window, the two assessments pulled in opposing directions for your choice.', 'In zumindest einem Teil des gespeicherten Analyseabschnitts wiesen die beiden Bewertungen für deine Wahl in entgegengesetzte Richtungen.'),['/selected_relations/support_dynamics/cognitive_affective_friction/mean_post_emergence'])
        add('TRAJECTORY_SCOPE','evolution',tr('Only saved summaries are available. They do not show the complete sequence, so I cannot say whether a change was continuous or how many reversals occurred.', 'Es liegen nur gespeicherte Zusammenfassungen vor. Der vollständige Verlauf fehlt; deshalb kann ich nicht sagen, ob eine Veränderung durchgehend war oder wie oft sie sich umkehrte.'),['/limitations'])
        # Orion facts: one observed value per variable, keeping validated unit and freshness.
        observations=(evidence.get('context') or {}).get('raw_observations') or []
        for variable, unit, en, german in [('temperature','°C','Temperature','Temperatur'),('rain','mm/h','Rain','Regen'),('windSpeed','m/s','Wind','Wind')]:
            eligible=[(i,o) for i,o in enumerate(observations) if o.get('source')=='orion' and o.get('variable')==variable and o.get('status')=='OBSERVED' and o.get('unit')==unit and o.get('source_metadata',{}).get('normalization_eligible') is True and finite(o.get('numeric_value'))]
            unique={o['numeric_value'] for _,o in eligible}
            if len(unique)!=1:continue # Different readings are not silently averaged or picked.
            i,o=eligible[0];value=format(o['numeric_value'],'.15g');prefix=f'/context/raw_observations/{i}'
            add('OBSERVED_'+variable,'observations',f'{german if de else en}: {value} {unit}.',[prefix+'/numeric_value',prefix+'/unit',prefix+'/status',prefix+'/source',prefix+'/source_metadata/normalization_eligible'])
        relations=(evidence.get('selected_relations') or {}).get('by_need') or []
        if any(r.get('belief_source')!='observed' or r.get('direct_user_response') is not True for r in relations):
            add('RELATIONS_INCLUDE_ESTIMATES','model_detail',tr('Some need–mode relationships used here are estimated; they are not all answers you gave directly.', 'Einige hier verwendete Beziehungen zwischen Bedürfnissen und Verkehrsmitteln sind geschätzt; sie stammen nicht alle aus deinen direkten Antworten.'),['/selected_relations/by_need'])
    story['message']=' '.join(s['text'] for section in ('selection','affinities','tensions','balance','comparison') for s in story['statements'] if s['section']==section)
    story.pop('template_id',None)
    story['template_id']=digest(story)
    return story
