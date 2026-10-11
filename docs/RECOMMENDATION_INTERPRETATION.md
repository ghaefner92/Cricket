# Recommendation interpretation — Cricket 0.4.4

The terminal winner remains HOTCO's available-mode argmax. Presentation uses
the final top-two activation margin, not the smallest gap in the trajectory.
Unavailable modes are excluded. One available mode is not a competitive win.

The final `settling_dwell` window must preserve the winner, meet the action
and full-state rate thresholds throughout, and avoid persistent terminal
co-dominance. The existing 0.01 practical margin and 0.25 close-window fraction
are provisional descriptive thresholds, not validated behavioural boundaries.
Initial rivalry remains available as a trajectory diagnostic only.

Route-conditioned summaries additionally check two symmetric local stresses:
winner/rival belief edges shift by +/-0.02 in the direction of need signs,
clipped to [-1,1]. A half-step RK4 repeat checks numerical sensitivity. Checks
retain the original context force; they do not save changes to the passport.
The numerical comparison requires the winner to persist and the final margin
to exceed twice the maximum terminal action discrepancy. Belief stresses must
retain a margin above the practical threshold for the assessed-stable label.
Identical effective context forces share checks within a request only.

These are limited local scenarios. Route-duration uncertainty, all individual
edge perturbations, global parameter uncertainty, and empirical calibration
are not assessed. Scenario retention is never presented as choice probability.
The additional simulations can increase response time for distinct contexts.

States: CLEAR, NEAR_TIE, UNSETTLED, SENSITIVE, ONLY_OPTION, UNKNOWN. Downstream
explanations must not treat an unfamiliar state as a clear recommendation.
The native banner preserves a shared leading mode even for cautious states;
conflicting route-conditioned winners produce a context-dependent message.
It speaks in the companion's first person in English and German.

New searches receive these diagnostics; saved historical simulations retain
their original results. No historical choices or journeys are rewritten.

Build: Android assembleDebug. No automated tests run for this change.
Future validation should assess numerical convergence, perturbation scope,
threshold sensitivity, held-out users/trips, actual choices and separately
reported trip satisfaction. Calibration requires independent observed data.
