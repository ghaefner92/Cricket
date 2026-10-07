"""Friendly wording of the same checked facts. No added inference or simulation."""
from choice_story_legacy_v1 import render_choice_story as render_legacy_story
from choice_evidence_v1 import digest

VERSION = 'choice-story-1.1'


def render_choice_story(evidence, *, language='en'):
    story = render_legacy_story(evidence, language=language)
    de = language == 'de'
    def tr(en, german): return german if de else en
    fixed = {
        'ALIGNED_CLEAR': tr('Your choice and the clear favourite in this simulation line up.', 'Deine Wahl passt zum klaren Favoriten dieser Simulation.'),
        'CHOICE_IN_AMBIGUOUS_PAIR': tr('You picked one of these two closely matched options.', 'Du hast eine dieser beiden ähnlich bewerteten Möglichkeiten gewählt.'),
        'CHOICE_BELOW_AMBIGUOUS_PAIR': tr('In this run, the model leans more towards both of those options than towards the one you chose.', 'In diesem Durchlauf spricht das Modell stärker für diese beiden Möglichkeiten als für deine Wahl.'),
        'COMPARISON_UNKNOWN': tr('I can’t clearly compare your choice with a favourite from these saved results.', 'Aus diesen gespeicherten Ergebnissen kann ich deine Wahl nicht eindeutig mit einem Favoriten vergleichen.'),
        'JOINT_SUPPORT': tr('Looking at your priorities and beliefs, the model points towards this choice. Its emotional side points the same way.', 'Mit deinen Prioritäten und Überzeugungen spricht das Modell für diese Wahl. Seine emotionale Seite weist in dieselbe Richtung.'),
        'JOINT_OPPOSITION': tr('Looking at your priorities and beliefs, the model leans away from this choice. Its emotional side points the same way.', 'Mit deinen Prioritäten und Überzeugungen spricht das Modell eher gegen diese Wahl. Seine emotionale Seite weist in dieselbe Richtung.'),
        'BALANCE_ZERO': tr('One or both sides of the model stay neutral towards this choice at the end.', 'Am Ende bleibt mindestens eine der beiden Seiten des Modells dieser Wahl gegenüber neutral.'),
        'BALANCE_UNKNOWN': tr('I don’t have enough saved information to compare the two sides of the model.', 'Mir fehlen gespeicherte Angaben, um die beiden Seiten des Modells zu vergleichen.'),
        'CONTEXT_NO_CHANGE': tr('With the available environmental readings, the saved model results stayed the same.', 'Mit den verfügbaren Umweltmesswerten blieben die gespeicherten Modellergebnisse gleich.'),
        'OBSERVATION_TIME_UNKNOWN': tr('Some readings don’t say when they were taken, so I can’t tell how recent they are.', 'Bei einigen Messwerten fehlt die Uhrzeit. Deshalb kann ich nicht sagen, wie aktuell sie sind.'),
        'GEOMETRY_UNVERIFIED': tr('These environmental readings belong to an extra path used for this comparison. I can’t confirm that it follows your chosen route exactly.', 'Diese Umweltmesswerte gehören zu einem zusätzlichen Verlauf für diesen Vergleich. Ob er genau deiner gewählten Route folgt, ist nicht bestätigt.'),
        'INTERPRETATION_LIMIT': tr('I’m reflecting what this model shows, not telling you how you felt. Choosing a route also doesn’t mean you have travelled it or reached your goals yet.', 'Ich spiegele dir, was dieses Modell zeigt, nicht wie du dich gefühlt hast. Eine gewählte Route bedeutet auch noch nicht, dass du sie gefahren bist oder deine Ziele erreicht hast.'),
        'TRAJECTORY_SCOPE': tr('I can compare the saved start and end points, but I can’t see every step in between.', 'Ich kann die gespeicherten Anfangs- und Endwerte vergleichen, aber nicht jeden Schritt dazwischen sehen.'),
        'OPPOSITION_DURING_WINDOW': tr('At some points in this run, the two sides of the model pulled in different directions.', 'An einigen der ausgewerteten Punkte zogen die beiden Seiten des Modells in unterschiedliche Richtungen.'),
        'RELATIONS_INCLUDE_ESTIMATES': tr('Some links between your priorities and transport modes are estimates, rather than answers you gave directly.', 'Einige Verbindungen zwischen deinen Prioritäten und Verkehrsmitteln sind Schätzungen, keine direkten Antworten von dir.'),
        'NO_CONTEXT_SIMULATION': tr('Your choice is saved, but there is no contextual simulation here for me to explain.', 'Deine Wahl ist gespeichert, aber hier liegt keine Simulation mit Umweltkontext vor, die ich erklären könnte.'),
        'CONTEXT_MISSING': tr('Some environmental information is missing. I won’t assume those conditions are favourable.', 'Einige Umweltangaben fehlen. Ich nehme deshalb nicht an, dass diese Bedingungen günstig sind.'),
        'GOALS_UNKNOWN': tr('I don’t have your saved weekly priorities for this search, so I won’t guess how your choice fits them.', 'Für diese Suche fehlen mir deine gespeicherten Wochenprioritäten. Deshalb rate ich nicht, wie deine Wahl dazu passt.')}
    support = (evidence.get('selected_relations') or {}).get('support_dynamics') or {}
    for row in story['statements']:
        rule=row['rule']
        if rule in fixed: row['text']=fixed[rule]
        if rule=='SAVED_CHOICE': row['text']+=tr(' Let’s see how it fits in this model.', ' Schauen wir, wie diese Wahl ins Modell passt.')
        if rule in ('GOALS_SUPPORT','GOALS_OPPOSITION'):
            labels=row['text'].split(': ',1)[1]
            row['text']=(tr('Here’s where your choice fits your weekly priorities: ', 'Hier passt deine Wahl zu deinen Wochenprioritäten: ') if rule=='GOALS_SUPPORT' else tr('There’s also a trade-off with these weekly priorities: ', 'Gleichzeitig gibt es eine Spannung zu diesen Wochenprioritäten: '))+labels
        if rule=='OPPOSED_CHANNELS':
            cognitive=support['cognitive_signed_input']['terminal']
            row['text']=tr('Your priorities and beliefs point towards this option in the model, while its emotional side pulls the other way.', 'Deine Prioritäten und Überzeugungen sprechen im Modell für diese Möglichkeit, während seine emotionale Seite in die andere Richtung zieht.') if cognitive>0 else tr('The model’s emotional side points towards this option, while your priorities and beliefs pull the other way in the model.', 'Die emotionale Seite des Modells spricht für diese Möglichkeit, während deine Prioritäten und Überzeugungen im Modell in die andere Richtung ziehen.')
        if rule=='DIFFERENT_CLEAR':
            from choice_template_v1 import MODES
            leader=MODES[language][evidence['deliberation']['contextual']['competition']['winner']]
            row['text']=tr(f'You took a different direction: in this run, the model clearly favoured {leader}.',f'Du hast eine andere Richtung gewählt: In diesem Durchlauf sprach das Modell klar für {leader}.')
        if rule=='AMBIGUOUS_LEADING_PAIR':
            from choice_template_v1 import MODES
            comp=evidence['deliberation']['contextual']['competition'];first=MODES[language][comp['winner']];second=MODES[language][comp['main_rival']]
            row['text']=tr(f'{first.capitalize()} and {second} came out close together here. Neither was a clear favourite.',f'{first} und {second} lagen hier nah beieinander. Keine der beiden Möglichkeiten war ein klarer Favorit.')
        if rule=='CONTEXT_CHANGE':
            delta=evidence['deliberation']['selected_activation_delta']
            row['text']=tr('With the available environmental readings, the model leaned more towards your option.' if delta>0 else 'With the available environmental readings, the model leaned less towards your option.' if delta<0 else 'The environmental readings changed part of the model’s result, but its final leaning towards your option stayed the same.', 'Mit den verfügbaren Umweltmesswerten sprach das Modell stärker für deine Möglichkeit.' if delta>0 else 'Mit den verfügbaren Umweltmesswerten sprach das Modell weniger für deine Möglichkeit.' if delta<0 else 'Die Umweltmesswerte veränderten einen Teil des Modellergebnisses. Die abschließende Tendenz zu deiner Möglichkeit blieb gleich.')
        if row['section']=='evolution' and rule.startswith('ENDPOINT_'):
            refs=row['evidence_refs'];channel='cognitive_signed_input' if any('cognitive_signed_input' in r for r in refs) else 'affective_signed_input'
            values=support[channel];initial,terminal=values['initial'],values['terminal']
            label=tr('The side based on your priorities and beliefs', 'Die Seite mit deinen Prioritäten und Überzeugungen') if channel=='cognitive_signed_input' else tr('The model’s emotional side', 'Die emotionale Seite des Modells')
            if rule=='ENDPOINT_SIGN_CHANGE':
                ending=tr('pointed away from your option at the start, but towards it at the end.', 'sprach am Anfang gegen deine Möglichkeit, am Ende aber dafür.') if terminal>0 else tr('pointed towards your option at the start, but away from it at the end.', 'sprach am Anfang für deine Möglichkeit, am Ende aber dagegen.')
            elif rule=='ENDPOINT_SUPPORT_CHANGE':
                ending=tr('backed your option at both ends, more strongly at the end.', 'sprach an beiden Zeitpunkten für deine Möglichkeit, am Ende stärker.') if terminal>initial else tr('backed your option at both ends, less strongly at the end.', 'sprach an beiden Zeitpunkten für deine Möglichkeit, am Ende weniger stark.')
            else:
                ending=tr('pointed away from your option at both ends, less strongly at the end.', 'sprach an beiden Zeitpunkten gegen deine Möglichkeit, am Ende weniger stark.') if terminal>initial else tr('pointed away from your option at both ends, more strongly at the end.', 'sprach an beiden Zeitpunkten gegen deine Möglichkeit, am Ende stärker.')
            row['text']=label+' '+ending
        if rule.startswith('OBSERVED_'):
            row['text']=tr('The available readings show ', 'Die verfügbaren Messwerte zeigen ')+row['text'][0].lower()+row['text'][1:]
    story['presentation_version']=VERSION
    story['rule_version']='choice-reflection-rules-1.2'
    order=['selection','affinities','tensions','balance','comparison','observations','context','evolution','uncertainty','model_detail']
    story['message']=' '.join(row['text'] for section in order for row in story['statements'] if row['section']==section)
    story.pop('template_id',None);story['template_id']=digest(story)
    return story
