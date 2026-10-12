# Mobility survey and HOTCO feasibility

Reviewed 2026-10-12. Source: `D:/Cricket/mobility_data.json`.
The source and active app/model were not modified. Aggregate counts can be
reproduced with `python validation/audit_mobility_dataset.py`.

## Findings

- 1,356 survey records, 682 distinct nonempty participant codes. Codes are not
  verified unique people; there are repeated codes and incomplete submissions.
- 669 records have parseable APP answers and all 11 required importance ratings.
- 671 records have parseable affect answers; 567 have numeric ratings for all
  four canonical modes under the provisional mappings below.
- No record has all 44 canonical belief cells observed. Most records with
  beliefs have three selected needs per answered mode; these are sparse data.
- 526 distinct codes have at least one record with consent Y, both screen flags
  zero, complete need ratings and complete canonical valences.
- 484 of those codes have at least one qualifying record with an observed
  canonical belief. Adding submission date and attention response equal to 7
  yields 459 codes. These are candidate counts, not a finalized research sample;
  the attention instruction and duplicate selection rule require confirmation.
- Three nonempty APP values fail JSON/object parsing. Their source is preserved.

## Measurement bridge

APP.ratings maps env/health_activity/crowding/flex/cost/time/safety_accident/
safety_crime/comfort_physical/reliable/health_infection to the existing 11
HOTCO needs, normalized with `(r-1)/6`. These are survey importance declarations,
not newly measured daily goals.

APP.beliefs maps car_driver/bike/pt/walk to car/bike/pt/walk, preserving observed
1-7 answers and normalizing with `(r-4)/3`. APP.ranking ranks needs, not modes.
Additional survey modes must not be silently collapsed into these four modes.

emoval.answers maps car_driver/bike/bus/walk to car/bike/pt/walk and uses `r/3`.
The bus-to-pt mapping and original bipolar belief/affect instructions require
semantic verification. NA/null remain missing, never neutral responses.

MOBIL contains s1/s2 modal items and other coded questions. Their complete item
wording/scales are not in this export. Frequencies and skippedModes are not
interpreted as current modal availability or prospective route choices.

## Feasible offline workflow

1. Freeze source digest, verify questionnaire anchors and define duplicate,
   completion, screening and attention policies before selecting records.
2. Construct normalized need/valence inputs and sparse observed belief cells.
3. Use existing AdaptivePassportV1.predict_all or personalize with
   require_complete_k=False for historical sparse answers; label remaining cells
   estimated and archive artifact digest. This is an offline historical adapter,
   not a claim that the current four-question onboarding was completed.
4. Run HOTCO with explicit experimental availability scenarios. No explicit
   current availability declaration is present in the inspected export.
   Scenario masks must be marked assumed and remain outside active app storage.
5. Export research Cognitive Passports, trajectories, ranking/margins and
   observed/estimated provenance. Production passport builders must not falsely
   claim all 44 beliefs or availability were participant declarations.
6. Compare static weighted support and HOTCO with the same inputs; vary
   availability and belief estimates to assess sensitivity. This evaluates
   model behavior, not accuracy of unobserved choices or psychological dynamics.

The frozen Ridge artifact reports 388 training persons and 4,146 observed belief
rows. Overlap between that training cohort and this export is unknown. Recover
split identifiers before using this dataset as an independent estimator test.

This export supports population-profile simulations and possibly cross-sectional
comparisons after checking the MOBIL codebook. It has no identified prospective
per-trip choices, route alternatives, environmental trip exposure, or subsequent
Experience responses with which to validate longitudinal Bayes or HOTCO choice
prediction. Existing data can reduce profile-collection work; they do not replace
the prospective behavioral study discussed previously.
