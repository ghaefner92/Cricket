# Everyday mobility — Cricket 0.4.26

## Presentation

Meet your companion is now a full-width scene without the outer card's inset. Its aspect ratio changes from 16:10 to 4:3; the existing height-based sprite renderer naturally enlarges the companion. Original images, frame metadata, eight-second animation, and JRPG controls are retained.

Travel kit is renamed Your everyday mobility / Deine Mobilität im Alltag. The full-width scene has aspect ratio 3:2, a larger foreground companion/vehicle, and a cropped rather than stretched background. Original PNGs are unchanged. Landmark captions are removed. A 48 dp info button in the top right opens a short location/history dialog and an optional source link.

## Habits and route availability

The main question is How do you usually get around day to day? / Wie bist du im Alltag normalerweise unterwegs? Multiple habitual modes, including walking, can be marked. Each of four modes receives Usually / Not usually; all four must be answered. Reporting no habitual modes is allowed.

Answers are stored separately in `cricket.mobility-habits.v1`, with version, updated_at, and four Boolean answers. Previous availability answers are never copied into habits. Habits are not injected into HOTCO, valences, beliefs, or route eligibility. They are included as descriptive provenance in the confirmed Passport's local sourceSnapshot.habitual_mobility. Generic local backup exports include the new key. New renewal drafts snapshot this key for cancellation restoration.

The collapsed Options for route suggestions section controls the existing `imiq.experimental.availability.v1` record. Less familiar modes can remain eligible. Existing explicit availability is preserved. Missing availability is displayed as included, can be explicitly changed, and is committed as true only when Continue is pressed. Thus Not usually never disables a mode. At least one route option must remain enabled. The backend bootstrap and route availability contracts retain their existing four Boolean fields.

## Historical sources

Dialog copy is brief English/German paraphrase, with a source link:

- [Grüne Zitadelle project history](https://www.gruene-zitadelle.de/das-projekt): Hundertwasser design and 2005 completion.
- [Berlin's official World Clock page](https://www.berlin.de/sehenswuerdigkeiten/3561749-3558930-weltzeituhr.html): 1969 inauguration.
- [Kassel's official water features page](https://www.kassel.de/buerger/kunst_und_kultur/parks_und_gaerten/wilhelmshoehe/wasserspiele.php): gravity-driven water features without pumps. The motorway/park juxtaposition is described as an artistic illustration.
- [German UNESCO Commission, Cologne Cathedral](https://www.unesco.de/staette/koelner-dom/): Gothic construction 1248–1880 and UNESCO recognition in 1996.

## Release checks

APK assembled successfully with `node scripts/build.mjs assembleDebug`. Installed by replacement (preserving local data) on phone RZCY70K63HV. No automated tests were requested for this change. User review of the enlarged scenes and new questions remains pending.
