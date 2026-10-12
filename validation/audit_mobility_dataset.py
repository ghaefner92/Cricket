"""Read-only aggregate audit of survey data against current HOTCO inputs."""
import hashlib
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "mobility_data.json"
NEEDS = ("env", "health_activity", "crowding", "flex", "cost", "time",
         "safety_accident", "safety_crime", "comfort_physical", "reliable", "health_infection")
MODES = {"car": "car_driver", "bike": "bike", "pt": "pt", "walk": "walk"}
EMOTION = {"car": "car_driver", "bike": "bike", "pt": "bus", "walk": "walk"}


def rating(value, low, high):
    return (isinstance(value, (int, float)) and not isinstance(value, bool)
            and low <= value <= high and float(value).is_integer())


def main():
    raw = SOURCE.read_bytes()
    root = json.loads(raw)
    rows = root["responses"]
    counts = Counter()
    fields = Counter()
    keys = {name: Counter() for name in ("APP", "MOBIL", "emoval", "PROFILE")}
    belief_counts = Counter()
    per_mode = {mode: Counter() for mode in MODES}
    raw_values = {"needs": Counter(), "beliefs": Counter(), "valences": Counter()}
    codes = Counter()
    candidate_codes = Counter()
    candidate_attention_codes = Counter()
    codes_with_app = Counter()
    completed_candidate_codes = Counter()
    candidate_observed_codes = Counter()
    candidate_submitted_attention_codes = Counter()
    dates = []
    for row in rows:
        counts["records"] += 1
        fields.update(row.keys())
        if row.get("participantCode"):
            codes[row["participantCode"]] += 1
        if row.get("startdate"):
            dates.append(row["startdate"])
        consent = row.get("consent") == "Y"
        screen = str(row.get("CALCSCREENOUT")) == "0" and str(row.get("SCREENCHECK")) == "0"
        counts["consent_yes"] += consent
        counts["screen_flags_both_zero"] += screen
        counts["submitdate_present"] += bool(row.get("submitdate"))
        parsed = {}
        for name in keys:
            value = row.get(name)
            if value is None or value == "":
                counts[name + "_absent"] += 1
                parsed[name] = {}
                continue
            try:
                obj = json.loads(value) if isinstance(value, str) else value
                if not isinstance(obj, dict):
                    raise ValueError("not an object")
                answer = obj.get("answers", obj)
                if not isinstance(answer, dict):
                    raise ValueError("answers not an object")
                parsed[name] = answer
                keys[name].update(answer.keys())
                counts[name + "_parseable"] += 1
            except (ValueError, TypeError):
                counts[name + "_parse_error"] += 1
                parsed[name] = {}
        app = parsed["APP"]
        needs = app.get("ratings", {})
        beliefs = app.get("beliefs", {})
        emotions = parsed["emoval"]
        complete_needs = all(rating(needs.get(n), 1, 7) for n in NEEDS)
        counts["all_11_needs_valid"] += complete_needs
        attention = needs.get("attn_check") == 7
        counts["attention_equals_7_candidate_not_confirmed_rule"] += attention
        all_valences = all(rating(emotions.get(EMOTION[m]), -3, 3) for m in MODES)
        counts["all_4_canonical_valences_valid"] += all_valences
        candidate = consent and screen and complete_needs and all_valences
        counts["candidate_needs_and_valences"] += candidate
        counts["candidate_plus_attention_equals_7"] += candidate and attention
        code = row.get("participantCode")
        if code and parsed["APP"]:
            codes_with_app[code] += 1
        if candidate and code:
            candidate_codes[code] += 1
            if attention:
                candidate_attention_codes[code] += 1
            if row.get("submitdate"):
                completed_candidate_codes[code] += 1
        total = 0
        for mode, source_mode in MODES.items():
            observed = beliefs.get(source_mode, {})
            n = sum(rating(observed.get(need), 1, 7) for need in NEEDS)
            total += n
            per_mode[mode]["with_any_belief"] += n > 0
            per_mode[mode]["with_all_11_beliefs"] += n == 11
            per_mode[mode]["observed_belief_cells"] += n
            per_mode[mode]["numeric_valence"] += rating(emotions.get(EMOTION[mode]), -3, 3)
        belief_counts[total] += 1
        if candidate and code and total > 0:
            candidate_observed_codes[code] += 1
        if candidate and code and total > 0 and attention and row.get("submitdate"):
            candidate_submitted_attention_codes[code] += 1
        counts["all_44_canonical_beliefs_valid"] += total == 44
        for need in NEEDS:
            if need in needs:
                raw_values["needs"][str(needs[need])] += 1
        for mode_values in beliefs.values():
            if isinstance(mode_values, dict):
                for value in mode_values.values():
                    raw_values["beliefs"][str(value)] += 1
        for mode in EMOTION.values():
            if mode in emotions:
                raw_values["valences"][str(emotions[mode])] += 1
    artifact = json.loads((ROOT / "app-backend/services/dyconet/artifacts/adaptive_passport_v1.json").read_text())
    report = {
        "source": "mobility_data.json", "source_sha256": hashlib.sha256(raw).hexdigest(),
        "root_keys": list(root), "counts": dict(counts), "top_level_fields": dict(fields),
        "unique_nonempty_participant_codes": len(codes),
        "duplicate_nonempty_code_extra_records": sum(n-1 for n in codes.values()),
        "unique_candidate_codes_needs_valences": len(candidate_codes),
        "unique_candidate_codes_attention_equals_7": len(candidate_attention_codes),
        "unique_submitted_candidate_codes": len(completed_candidate_codes),
        "unique_candidate_codes_with_observed_canonical_beliefs": len(candidate_observed_codes),
        "unique_submitted_attention_candidate_codes_with_observed_beliefs": len(candidate_submitted_attention_codes),
        "codes_with_multiple_parseable_APP_records": sum(n > 1 for n in codes_with_app.values()),
        "startdate_range": [min(dates), max(dates)] if dates else [],
        "decoded_answer_keys": {name: dict(value) for name, value in keys.items()},
        "canonical_belief_cells_per_record": dict(sorted(belief_counts.items())),
        "canonical_modes": per_mode, "observed_value_counts": raw_values,
        "existing_estimator_training_scope": artifact.get("training_scope"),
        "limitations": [
            "Counts describe records, not verified unique participants or valid empirical trips.",
            "Attention threshold 7 is a candidate count; original item instructions not verified.",
            "APP.pt to HOTCO.pt and emoval.bus to HOTCO.pt require semantic confirmation.",
            "Frequency and skippedModes are not treated as explicit modal availability.",
            "No estimates, simulations, active Passport changes or source modifications performed.",
            "Training cohort overlap with frozen estimator is not established.",
        ],
    }
    output = ROOT / "output/mobility-data-review/audit.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("counts", "unique_nonempty_participant_codes",
          "duplicate_nonempty_code_extra_records", "unique_candidate_codes_needs_valences",
          "unique_candidate_codes_attention_equals_7", "unique_submitted_candidate_codes",
          "unique_candidate_codes_with_observed_canonical_beliefs",
          "unique_submitted_attention_candidate_codes_with_observed_beliefs",
          "codes_with_multiple_parseable_APP_records", "startdate_range", "canonical_belief_cells_per_record",
          "canonical_modes", "existing_estimator_training_scope")}, indent=2))


if __name__ == "__main__":
    main()
