# Cricket 0.4.10 — Home

Home shows the companion's question and animated Start button, current weather
and a priority ranking. Passport-ready copy and the duplicated Passport button
are removed. Rankings use saved Passport allocation, preserve equal ranks for
equal points, and show points out of ten rather than recommendation confidence.

Each Home composition calls GET /api/dyconet/weather independently of route
searches. The endpoint constructs a fresh OrionQueryListProvider and requests
Weather within 5 km of the selected origin, or Magdeburg if none is selected.
The area is labelled. It does not request device GPS or send Passport data.
Sensor units follow the existing explicit device contracts. Query time is
recorded separately from the measurement timestamps; missing sensor timestamps
are not invented. Response caching is disabled. Refresh offers a manual retry.

Weather uses the same clickable sprite/value component in a two-column grid,
with attribute explanations. No previous weather snapshot is shown as fresh
when a new request is loading or fails. Historical route snapshots are intact.
Animations respect motion settings. Content remains scrollable for large text.

Android assembleDebug compiled and APK installed. No automated tests run.
