# Cricket 0.4.5 — map and route explorer

## 0.4.6 refinement

0.4.8: the shared strip excludes explicit JSON-null and missing units before
unit consistency checks; these no longer hide valid readings from other
sensors. Validated temperature unit aliases display as °C. The provider name
is removed. Each icon/value opens an attribute explanation, reading and
request timestamp; missing readings remain unknown. The timestamp identifies
the search query, not an invented sensor measurement time. New searches use
their newly returned weather snapshot; old history keeps its original snapshot.

Zoom and fit buttons are removed from all map views; pan and pinch remain.
The existing GPS follow-position action remains available during tracking.
Navigation/mode sprites occupy one horizontally scrollable line. A separate
single-line Orion weather strip sits immediately below it, shared across
the search rather than repeated inside route or choice cards. Repeated sensor
observations are deduplicated; differing readings appear as ranges, conflicting
units or missing readings as an em dash. Each icon/value has a spoken label.
Both lines scroll horizontally if needed, retaining the full map height.

Routes and Map now open the same immersive explorer. Small 24 dp vector
sprites use 48 dp accessible touch targets. Mode labels, route time/distance
and transit departure appear in a compact bottom summary. Details opens a
scrollable modal sheet retaining recommendations, weather, profile links,
transit legs, later departures and source notices.

Choosing a route saves the original choice and opens a selected-route map
preview. GPS start and manual confirmation use the existing tracking flow.
Existing recordings and experiences open their saved journey; an active trip
must be managed before another can start. Expired departures cannot start GPS.
Choice narration is loaded when explicitly opened, rather than on selection.

The explorer and preview hide the app title and bottom navigation; Home and
Search remain available as accessible sprite buttons. Recording continues to
use the existing immersive tracking screen with pause, finish and cancel.

Weather variables have distinct vector sprites and gentle motion. Unknown
readings remain unknown, with stationary individual icons. Profile need keys
map to their corresponding goal emblems. Animation respects motion settings.

Android assembleDebug compiled. Automated tests were not requested or run.
This is a map/recording interface, not turn-by-turn guidance or map matching.
