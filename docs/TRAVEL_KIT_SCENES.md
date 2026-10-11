# Travel kit and companion frames — Cricket 0.4.25

## Flow

Onboarding now has six steps: companion → travel kit → goals → feelings → connections → passport. Companion selection requires only a nonempty name; availability is collected separately. Travel kit shows one mode at a time with arrows, horizontal swipe, four direct mode buttons, and persistent Yes/No answers. Next mode visits the next unanswered mode. Continue to goals requires all four answers and at least one available mode. Back from goals returns to travel kit. The new step is recognized when restoring the app. Starting a new Passport draft begins at companion selection; the existing confirmed Passport remains available through the existing renewal mechanism. New renewal drafts additionally snapshot companion and availability records without clearing their prefilled values; cancelling restores those records, including absence of a previously missing record.

No availability answers, current Passport or journey history are reset by installation. Existing answers are reused. Travel-kit answers use the same local availability record and Passport generation contract as before.

## Art and motion

Four new opaque backgrounds were generated using the built-in `image_gen.imagegen` tool with downloaded architectural photographs and the existing Harz background as style reference. Exact prompts, source output paths and inputs are recorded in [TRAVEL_KIT_PROMPTS.json](TRAVEL_KIT_PROMPTS.json).

Saved assets, under `android-native/app/src/main/assets/art/jrpg/`:

- `travel-kit-pt-background-v1.png`: Grüne Zitadelle, Magdeburg.
- `travel-kit-walk-background-v1.png`: Alexanderplatz, Berlin.
- `travel-kit-car-background-v1.png`: Autobahn vignette with Herkules and Wasserspiele, Kassel. This is an artistic juxtaposition, not an exact geographic view from an actual road.
- `travel-kit-bike-background-v1.png`: Rhine cycling lane with Kölner Dom and Hohenzollernbrücke.

The selected companion's existing transport atlas supplies walking, cycling, car and tram animation. Sprites traverse the foreground, with a mode-specific ground position and 12–20 second cycle. Scene crossfade is 420 ms. Ambient water, cloud and glint effects are drawn in Compose. New scene clocks stop in the background. Reduced/off motion holds a centered static pose and omits transition/ambient motion.

Meet your companion retains its original four backgrounds, action atlases, frame metadata and eight-second action loops. Only the surrounding controls change: layered gold/pale/mint pixel corner ornaments, a gentle frame glint and relief arrow buttons with pressed feedback. The ornaments extend the code-drawn Cricket icon system.

## Photographic references

Photographs are development references in `docs/art-references/`, not bundled as UI imagery. They are architectural inputs for new pixel-art illustrations.

- Magdeburg: [official Grüne Zitadelle gallery](https://www.gruene-zitadelle.de/bildergalerie), `magdeburg.jpg`.
- Berlin: [Städte-Fotos, der Nordhesse](https://www.staedte-fotos.de/bild/Deutschland~Berlin~Stadtansichten/15010/berlin---alexanderplatz-weltzeituhr-und-fernsehturm.html), `berlin.jpg`.
- Kassel: [University of Toronto Summer Abroad gallery](https://summerabroad.utoronto.ca/program/germany-kassel-CSSG), `kassel.jpg`.
- Köln: [Hanse, KölnTourismus / Dieter Jacobi](https://www.hanse.org/de/hanse/koeln), `koeln.jpg`.

## Validation

Generated backgrounds and the existing transport atlas were visually inspected. APK assembled successfully with `node scripts/build.mjs assembleDebug` and installed with `adb install -r` on phone RZCY70K63HV. Installed package reports versionName 0.4.25 / versionCode 36. The activity was launched, USB reverse tcp:8077 restored, and backend health returned status ok. No automated tests were requested for this UI/art change. Physical-device interaction review is pending.
