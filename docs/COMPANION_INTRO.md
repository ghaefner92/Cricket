# Companion presentation — 0.4.27

## Name

The Android launcher label, startup title, app bar, settings/about and tracking notification channel display Companion. Application ID `de.ovgu.imiq.cricket`, database/storage keys, backup schema and tracking intent/channel identifiers are retained so replacement installation preserves existing data and compatibility. Export filenames use Companion. Internal class names remain stable.

## Startup scene

A nine-second cold-start presentation shows four upper-body greetings emerging from the screen corners. Naturalist: top left; robot: top right; explorer: bottom left; creature: bottom right. Portraits crop the original body frames at 69% height and slide smoothly into view. Existing robot/explorer/creature greeting atlases keep their 16 poses. The naturalist has a new 24-pose atlas without embedded dogs or mushrooms so separate actors can interact with her.

Two puppies emerge from the naturalist's backpack around 1.15–1.45 seconds. The first reaches her raised hand at 1.75 seconds and then carries the luminous Amanita; the second follows. Their eight-pose running cycles are independent of the interpolated travel paths. They arrive by about 4.45 and 5.15 seconds and sit above the first C and final N in COMPANION. Letter positions are obtained from the actual title text layout. At 6.45 seconds, the carrier opens its mouth; the separate mushroom prop falls over 0.75 seconds and rests beside the title with a soft light.

The original university/IMIQ/funding logos remain in a small white card below the title. Loading is shown while local initialization is pending; once ready, a Let's go button lets the user enter early. The intro auto-dismisses after nine seconds. Reduced/off motion shows the completed static scene and exposes the entry button immediately when ready. Animation frame callbacks run only while lifecycle state is STARTED. Existing ViewModel timing avoids replay on rotation or ordinary activity resume.

## New art

Generated with the built-in `image_gen.imagegen` tool using the existing naturalist greeting atlas as identity/style reference. Exact prompts and saved/source paths: [COMPANION_INTRO_PROMPTS.json](COMPANION_INTRO_PROMPTS.json).

Project assets in `android-native/app/src/main/assets/art/jrpg/`:

- `companion-intro-naturalist-v1.png`: 6×4, 24 poses (greeting, surprised reach, amused reaction, wave).
- `companion-intro-dogs-v1.png`: 8×4, 32 poses (each dog: eight running and eight settling/release poses).
- `companion-intro-amanita-v1.png`: 4×2, eight glow poses.
- `companion-intro-atlas.json`: source-cell metadata for new actors and reused greetings.

Source originals are retained under Codex generated_images. The new PNGs have genuine alpha transparency. A read-only Pillow inspection writes metadata and a report, never edits image pixels: `android-native/scripts/inspect-intro-atlas.py`, [COMPANION_INTRO_ASSETS.json](COMPANION_INTRO_ASSETS.json).

## Checks

All generated assets visually inspected; 24/32/8 unique cells and transparent alpha recorded. APK assembled successfully with `node scripts/build.mjs assembleDebug`. Replacement install succeeded on Android phone RZCY70K63HV, app activity launched, and USB reverse tcp:8077 restored. Installed package reports versionName 0.4.27 / versionCode 38; backend health returns status ok. No automated tests were requested for this presentation change. Physical-device review of the complete scene remains pending.
