# Smooth startup avatars — Companion 0.4.32

Only `IntroCornerCast`, the four large companions in the opening screen, uses the new smooth artwork. Their grid layout, poses, entry paths, rotation angles, displayed size, timing and frame dissolves remain compatible with the current intro. Character selection, travel kit, map avatars and other screens continue using their existing art.

## Artwork and rendering

The built-in image generation tool repainted the original sheets with continuous curved contours, clean anti-aliasing and softer cel shading, preserving each character's outfit, palette, proportions and pose order. Naturalist has 24 poses; robot, explorer and creature have 16 each (72 poses total). Originals are retained alongside the new versioned PNGs.

Source files:

- `android-native/app/src/main/assets/art/jrpg/companion-intro-naturalist-smooth-v2.png`
- `android-native/app/src/main/assets/art/jrpg/companion-intro-robot-smooth-v2.png`
- `android-native/app/src/main/assets/art/jrpg/companion-intro-explorer-smooth-v2.png`
- `android-native/app/src/main/assets/art/jrpg/companion-intro-creature-smooth-v2.png`

The intro loads each sheet from its frame metadata and uses `FilterQuality.High` for enlargement and rotation. Metadata follows actual generated image dimensions, avoiding assumptions about requested output sizes. Increasing color bit depth alone does not remove coarse contours; the change combines a smooth repaint with better image sampling.

Exact prompts, reference images, and generated/source paths are recorded in `COMPANION_INTRO_SMOOTH_PROMPTS.json`. Actual dimensions and alpha information are recorded by the existing read-only inspection script in `COMPANION_INTRO_ASSETS.json`.

The tool retained the original bitmap sizes despite the larger sizes requested in the prompts: naturalist 1536 × 1024; the other three 1254 × 1254. The visual improvement is the repainted contour/shading and high-quality sampling, not a claimed increase in bitmap dimensions. All sheets have genuine transparent alpha and the expected number of unique cells.

## Delivery

VersionCode 43 / versionName 0.4.32. Build command: `node scripts/build.mjs assembleDebug` completed successfully. Installed on the connected Samsung SM-A566B; version metadata confirmed. A physical-device screenshot was inspected at `.local-backups/companion-intro-smooth-0432.png` for smooth large contours, preserved composition and intact transparent backgrounds. USB port 8077 reverse restored. No automated tests were added or run for this visual change.
