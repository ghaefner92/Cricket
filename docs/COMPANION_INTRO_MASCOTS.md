# Companion opening — 0.4.29

The opening keeps four companions leaning in from the screen edges. Only the mushroom-carrying puppy travels to the C; the second puppy is removed from this presentation.

## New actors

- Explorer's brown spider climbs from the lower left and hangs from the first O with an interpolated silk thread and gentle sway.
- Robot's small cyan hovering pet flies from the upper right to the N and projects the node-wise HOTCO field: `τ·ȧ = m[−d·a + (U−a)E − (a−L)I]`. This is the equation implemented by `HOTCOCTv43.vector_field` in `app-backend/services/dyconet/hotco_ct_v4_3.py`, including the availability mask and bounds. It is a decorative equation, not a live numerical calculation.
- Creature's mint spirit arrives from the lower right and circles the measured start-button perimeter, leaving a continuous fading light trail. The Canvas has no pointer handler, so the button remains usable.
- The footer displays only the university and Intelligenter Mobilitätsraum. The existing typography pixels are retained; a separate monochrome Guericke bust opens/closes its mouth and winks. The funding block and separate IMIQ caption are removed from this screen.

## Animation and readiness

Four new transparent sprite sheets have 12 poses each (48 new authored poses). Frame rectangles follow each image's actual dimensions, preserve aspect ratios, and use brief eased dissolves. Paths, thread, hovering, and trail are interpolated in Compose. The lifecycle clock pauses rendering in the background. Reduced/off motion shows static actors, with no trail or moving portrait. The start button is conditional on `model.ready`; the intro never automatically dismisses.

The principal narrative retains the approximately 12-second mushroom landing/title glow, followed by quiet loops. The user may enter as soon as initialization finishes.

## Assets and provenance

Assets: `android-native/app/src/main/assets/art/jrpg/companion-intro-{spider,drone,spirit,portrait}-v1.png`.

Exact built-in image generation prompts, input references, and source/save paths: `COMPANION_INTRO_MASCOT_PROMPTS.json`. Read-only alpha/dimension inspection: `COMPANION_INTRO_ASSETS.json`; generated frame metadata: `companion-intro-atlas.json`. Original reference assets remain available.

## Delivery

Compiled with `node scripts/build.mjs assembleDebug` and installed successfully on the connected Samsung SM-A566B (versionCode 40, versionName 0.4.29). A physical-device screenshot was inspected for spacing, readable logo text, mascot placement and the unobstructed button; it is saved locally as `.local-backups/companion-intro-0429.png`. Backend health returned `ok`, and USB port 8077 reverse was restored. No automated tests were added or run for this visual change.
