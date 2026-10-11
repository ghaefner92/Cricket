# Cricket 0.4.13 — current position

A 48 dp accessible location-sprite button appears at the right centre of each
map, including route selection, chosen-route preview and tracking. It requests
fine/coarse permission when needed, displays an indeterminate spinner and
centres the map on a fresh GPS/network fix. The blue marker includes the
reported accuracy circle. Permission denial, disabled providers and timeout
have dismissible messages. Pan or pinch suspends follow as before.

The map request is separate from origin selection and journey recording. It
does not mutate addresses, routes, choices, Passport or stored GPS points.
Location updates are removed after the first usable fix, timeout or cancellation.
No background location service is started. Tracking continues independently.
Tracking/map fixes share the position marker with the newer monotonic timestamp
taking precedence; a retained tracking point is not substituted for a fresh fix.

Android assembleDebug compiled. No automated or outdoor GPS tests run.
