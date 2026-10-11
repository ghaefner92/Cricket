# Map experience — Cricket 0.4.19

- Location control uses BottomStart with 12 dp margins and the existing 48 dp touch target. OSM attribution remains on the opposite side.
- A is the chosen companion's existing animated walking atlas, anchored at the origin. B is a new code-drawn checkered finish flag. Small A/B badges and coordinate dots clarify the exact anchor. Markers move with the map's projection and preserve route and GPS overlays.
- Clear recommendations use the existing unanimous-clear assessment: gold frame, slow glint, sparkle badge and first-person caption. The mode icon and the primary itinerary card carry this appearance. Selection remains independent from recommendation; uncertain comparisons do not acquire an invented winner. Expired itineraries are not highlighted.
- Motion follows existing settings: full animates, reduced/off uses static frames and a static golden frame.
- Walking-to-transit limit defaults to 1500 m in settings and requests. One-time walkingDefaultVersion=2 migration replaces the old 500 m default; other existing limits remain. It is still editable. This is a routing constraint, not a maximum length for a standalone walking journey.
- Light readings show a moon when all usable illuminance observations equal 0. Missing readings remain unknown; mixed sensor readings retain the light icon. The explanation says darkness at the sensor rather than asserting astronomical night. Shared Home weather uses the same renderer.

Existing sprite assets reused. New flag, moon, sparkle and neon effects are native Compose drawing. No generated bitmap or backend contract changes are needed.

Validation: debug APK compilation. No automated tests or outdoor GPS trial run for this change.

## 0.4.20 — Avatar follows selected mode

The origin marker reuses the companion's transport atlas: walking, bicycle, car or public transport. Its mode follows the selected map route, including route taps and mode icons. During tracking it follows the saved journey selection rather than a different current search. The destination flag and coordinate anchors remain unchanged. Motion settings still apply.

Transport buttons have circular rims, a vertical light-to-dark gradient and 4 dp elevation for gentle depth. Upper bar spacing increases from 6 to 12 dp; touch targets remain 48 dp and the row scrolls on narrow screens. Recommended mode glow follows a circular path rather than a rectangular frame.
