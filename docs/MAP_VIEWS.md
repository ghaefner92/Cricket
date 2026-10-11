# Companion map views

Android 0.4.35 (version code 46) adds Light, Night and Mobility views.

## Interface

- The layers button at the upper right of the map opens a bottom sheet with previews and descriptions.
- Light uses warm neutral surfaces, muted parks and legible street names.
- Night uses the app's charcoal and plum palette with brighter labels.
- Mobility highlights mapped paths in blue, cycleways in green and rail lines and stop labels in purple. A small legend explains the colours.
- The selected view persists locally as `cricket.map-style.v1`, including across app restarts.
- Changing the view retains the camera, selected route, GPS follow state and avatars.
- Zoom, drag and rotation use native gestures. Rotating shows a compass below the layers control; tilt remains disabled so the route overlays stay in a flat map.

## Implementation

The map background now uses MapLibre Native Android OpenGL 13.5.3 in a Compose AndroidView. Compose route paths and animated companions use the native projection, so they remain aligned during map gestures. Existing origin/destination markers, route selection and GPS position display are retained.

The native view follows the host lifecycle and is destroyed on disposal. Initial framing fits the selected route and GPS follow recentres on position fixes. Styling does not affect route calculations or HOTCO.

The three JSON styles are bundled in `android-native/app/src/main/assets/maps/`. Regenerate them from the preserved Liberty source with:

```powershell
cd android-native
node scripts/build-map-styles.mjs
```

Mobility uses OpenMapTiles transportation classes/subclasses and POIs already supplied by the map. It is a cartographic view: coverage depends on mapped features, and it does not introduce live traffic, service schedules or new route alternatives.

## Network and sources

Style definitions are local; vector tiles, icons and fonts are downloaded directly by the phone from OpenFreeMap over HTTPS. The new map background does not use the backend's raster tile proxy. Internet access on the phone is therefore needed for uncached resources. A 25 second loading timeout offers a retry action; a fully rendered map clears the notice if connectivity recovers.

This changes the resource provider from the former backend-mediated raster map. OpenFreeMap receives requests for the map area viewed, including the normal network metadata of those requests. Companion does not attach journey histories or raw GPS tracks to these map resource requests.

Visible corner credits identify OpenStreetMap, OpenMapTiles and OpenFreeMap; the native attribution dialog provides links. The About section also identifies the renderer, provider and adapted design.

Upstream references:

- https://openfreemap.org/quick_start/
- https://github.com/hyperknot/openfreemap-styles
- https://github.com/maputnik/osm-liberty
- https://openmaptiles.org/docs/schema/
- https://github.com/maplibre/maplibre-native/releases/tag/android-v13.5.3

OpenFreeMap and Liberty license notices are bundled alongside the JSON styles. Companion modifies the palette, label prominence and mobility layers of the Liberty design.

## Delivery

Android debug compilation succeeded. No automated tests were added or run for this change. The connected Samsung displayed the vector map after internet access was enabled; the initial empty map was due to the phone being offline.

### Resumed validation — 2026-10-09

- Recovered the interrupted session: the final debug build succeeded and the Samsung reported version 0.4.35 / code 46. The install command had not yet returned a final result when the session ended.
- Re-ran `node scripts/build.mjs assembleDebug`: BUILD SUCCESSFUL.
- Checked all three bundled styles: valid JSON, unique layer IDs, existing source references and provider attribution. Light and Night have 109 layers each; Mobility has 111, including the footpath and cycleway overlays.
- Verified that the debug APK contains byte-identical copies of the three current styles and both bundled license notices.
- Device validation remains pending: no physical phone is connected through ADB. Check Light → Night → Mobility, camera and route preservation, saved selection after reopening, and the offline notice/retry on the connected phone. The earlier visual check established that Light loads online; it did not establish the complete three-view acceptance check.
