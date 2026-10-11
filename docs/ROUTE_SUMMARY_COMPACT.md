# Compact route summary — Companion 0.4.40

Android version code 51. The compact comparison, selected-journey and active recording panels share the same height cap. The existing route details sheet retains its layout, pixel typography, content and original valence scale.

## Layout

The panel height is `min(screenHeightDp * 0.15, 112 dp)`. Portrait uses two rows; short landscape windows use one row. Goals sit immediately after mode/actual service and departure information, in an adaptive two-row grid without horizontal scrolling. All nonzero connections to active needs appear: green rising arrows for favourable expectations, red falling arrows for unfavourable expectations. Missing/neutral connections have no arrow. Tapping the group opens a readable, scrollable list with explanations. Reduced motion keeps arrows static. The second row has duration, distance, estimated arrival and the mascot face.

Play selects and starts the journey with GPS, using the existing Android permission flow. An existing active journey opens instead of starting a second one. Saved previews retain GPS/manual choices. The active map uses the same compact panel: a seconds counter replaces planned duration, Pause/Resume replaces Play, and a red square replaces the text/ellipsis menu. Tapping the square directly finishes and opens Experience feedback. Long-pressing Stop, or Android Back, offers cancellation with the existing confirmation dialog. Cancellation opens Experience with retained history, without falsely confirming completion. Buttons retain spoken labels and an accessible cancel action. Unavailable/expired departures remain unselectable.

The smooth system typography is scoped to the panel. Existing route details are outside this theme. Labels have spoken equivalents, including the play action's complete meaning.

## Time and transit identity

- Walk/bike/car arrival estimates use the current phone time plus the provider's duration and are labelled as departure now.
- Transit keeps the provider's absolute arrival time and the existing countdown including the initial wait exactly once. Invalid/missing/expired timestamps show unknown arrival, without inventing a time.
- The clock refresh remains every 15 seconds. Routing metrics, stored searches and HOTCO inputs are not rewritten by these presentation functions.
- Active recording updates the counter every second. Elapsed time includes pauses, matching the stored tracking contract. Summary distance remains planned distance; Experience discloses observed GPS distance. Active walk/bike/car arrival is anchored to the actual journey start rather than moving forward with each counter tick.
- Service badges read `source_mode` and `line` from the actual transit legs, preserve transfer order and use a generic transport symbol when the vehicle type is unknown.
- Bus, tram and train pictograms come from Google's official Material Icons repository, converted to Android VectorDrawable. These are standard transport pictograms, not an MVB brand mark. Apache-2.0 is bundled in `assets/licenses/LICENSE.material-icons.txt`.

Sources: [bus](https://github.com/google/material-design-icons/blob/master/src/maps/directions_bus/materialicons/24px.svg), [tram](https://github.com/google/material-design-icons/blob/master/src/maps/tram/materialicons/24px.svg), [train](https://github.com/google/material-design-icons/blob/master/src/maps/train/materialicons/24px.svg), [license](https://github.com/google/material-design-icons/blob/master/LICENSE).

## Journey avatar and camera

Starting, pausing, resuming and stopping do not automatically centre the camera on the real GPS position. Finding the real position remains an explicit map action. The journey avatar stays at the selected origin until a recorded fix lies within 75 metres of a segment of the selected route; such a fix is projected onto that segment for presentation only. Remote fixes leave the avatar unchanged. Pausing cancels any interpolation and freezes the avatar; stopping opens Experience. GPS evidence remains unmodified and the real position keeps its separate blue marker. Separate route legs are never connected by the avatar projection.

## Mascot faces

Smooth Canvas head portraits follow the established mascot identities: robot → mini robot, explorer → brown spider, creature → mint spirit, naturalist → black puppy. Only heads are shown. Saved per-mode valences control the seven-step expression, brow/mouth curvature, blinking and gentle motion. Missing valence has no selected scale step. The animation pauses outside the visible lifecycle and becomes static with motion disabled/reduced. The details sheet keeps its original scale.

## Map credits

The visible line is now `© OpenStreetMap · OpenMapTiles`, in compact 10 sp system typography. The native interactive attribution dialog keeps the full provider links, including OpenFreeMap. [OpenFreeMap's attribution guidance](https://openfreemap.org/#attribution) explicitly permits omitting its own name from the visible credit; OpenStreetMap/OpenMapTiles attribution remains present.

## Verification

- Version 0.4.40 / code 51: `testDebugUnitTest assembleDebug` passed 16 tests, including four new regressions for remote GPS rejection, nearby route projection, paused/stopped state and disconnected route legs. APK installed on the Samsung. New interaction checks were initially prevented by the phone's PIN lock; the PIN was not requested or entered.
- Version 0.4.38 / code 49: `testDebugUnitTest assembleDebug` passed all 12 tests and the final APK was installed. Phone checks confirmed GPS start from the first Play, a seconds counter, Pause/Resume, the controls menu, Finish → Experience and Cancel → Experience with retained cancellation status. No experience answers were submitted. The panel still measures 315 / 2340 px (13.46%). Car shows a red negative connection for physical activity and green favourable connections for flexibility/time; cycling shows three favourable connections.
- Profile data is cached for the life of the route panel, and active journeys explicitly use the original search passport even after restarting the app. Counter updates no longer reread the full saved search.
- Review captures: `.local-backups/summary-goals-0438.png`, `.local-backups/summary-red-goals-0438.png`, `.local-backups/summary-resume-current.png`, `.local-backups/summary-experience-finished-0438.png`, `.local-backups/summary-experience-cancelled-0438.png`.
- `node scripts/build.mjs testDebugUnitTest assembleDebug` passed with 12 tests (8 existing contract tests, 4 new arrival/transit identity tests).
- After the requested 15% cap and play-button refinement, `node scripts/build.mjs assembleDebug` passed and 0.4.37 / code 48 was installed on the Samsung.
- The physical panel measured 315 px on the 2340 px screen: 13.46%, with the map spanning 1494 px.
- Checked arrival labels in all four modes, the Tram 1 symbol/line, naturalist puppy pleasant/unpleasant faces, and the unchanged details sheet on the phone. Existing goals/feeling dialogs remain available.
- Local review captures: `.local-backups/summary-transit-0437.png`, `.local-backups/summary-bike-0437.png`, `.local-backups/summary-details-0437.png`.
