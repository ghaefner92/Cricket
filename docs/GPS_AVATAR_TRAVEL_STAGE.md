# GPS avatar travel stage — 0.4.22

Update 0.4.73: the author removes the GPS estimate label/button and manual
correction dialog. Automatic motion inference remains; the historical control
description below is superseded. See CAR_TIME_TRAM_SPRITE_EXPERIENCE_0473.md.

During a tracked journey the avatar moves to retained GPS coordinates, interpolating to each new point over one second. Before the first retained fix it stays at A. After the first fix A remains a static origin marker and the avatar shows the user's recorded position. Destination and accuracy circle remain visible. Reduced/off motion snaps to the new coordinate; pausing stops sprite animation.

Walking, bicycle and car sprites follow the saved journey mode. Public transport journeys initially show walking. JourneyAvatarMotion incrementally examines retained points and the saved itinerary geometry, without requesting another location stream or sending additional GPS data.

Presentation heuristics (provisional, not calibrated transport recognition):

- Pair intervals 6–60 seconds, both fixes at most 30 m accuracy, same tracking segment.
- Boarding estimate: proximity to a planned transit line within 70 m plus point accuracy, measured speed at least 3.5 m/s, and speed after subtracting the larger accuracy radius from travelled distance at least 2.5 m/s.
- At least three consecutive qualifying pairs spanning at least 20 seconds before changing to transit. The existing ten-second location subscription normally makes this about 30 seconds or longer.
- Exit estimate: three qualifying pairs spanning at least 30 seconds at 0.3–2.2 m/s on a later walking segment, within 40 m plus accuracy, and at least 15 m closer to walking geometry than to transit geometry.
- Segment breaks, insufficient precision and inconsistent evidence reset pending transitions. Stops retain the current transit state.

The map labels this a GPS estimate. The avatar and compact indicator open a correction dialog with automatic, walking and public transport presentation choices. Corrections last while this map instance is open. They never modify the saved mode, journey contract, passport, choice, confirmed experience or actual_modes.

Limitations: GPS cannot establish boarding. Parallel roads, cars, missing/approximate geometry, tunnel gaps and walking beside a transit line can produce missed or mistaken estimates. Missing geometry keeps automatic display walking until enough evidence exists, or the user selects the transit sprite. No external activity recognition SDK was added.

Validation: debug APK compilation. Automatic thresholds require a real journey trial; no automated tests or field GPS trial were requested or run for this change.
