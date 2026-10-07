"""Local history -> evidence -> deterministic reflection. No new simulation."""
from flask import jsonify, request
from choice_evidence_v1 import build_choice_evidence, ChoiceEvidenceError
from choice_story_v1 import render_choice_story
from choice_groq_narrator import narrate_choice, configuration_status


def register_choice_explanation(app):
    @app.get('/api/dyconet/choice-explanation/status')
    def choice_explanation_status():
        return jsonify(configuration_status())

    @app.post('/api/dyconet/choice-explanation')
    def choice_explanation():
        if request.content_length is not None and request.content_length > 8 * 1024 * 1024:
            return jsonify(error='choice_explanation_too_large'), 413
        payload = request.get_json(silent=True)
        if not isinstance(payload, dict) or set(payload) - {'record', 'confirmed_snapshot', 'choice_index', 'language'}:
            return jsonify(error='invalid_choice_explanation_request'), 422
        if payload.get('language') not in ('en', 'de'):
            return jsonify(error='unsupported_choice_explanation_language'), 422
        try:
            evidence = build_choice_evidence(payload.get('record'),
                choice_index=payload.get('choice_index', -1),
                confirmed_snapshot=payload.get('confirmed_snapshot'))
            explanation = render_choice_story(evidence, language=payload['language'])
        except (ChoiceEvidenceError, KeyError, TypeError, ValueError, AttributeError):
            return jsonify(error='invalid_choice_explanation_evidence'), 422
        return jsonify(schema_version='cricket-choice-explanation-response-v1',
                       identity=evidence['identity'], evidence_id=evidence['evidence_id'],
                       explanation=explanation, narration=narrate_choice(explanation))
