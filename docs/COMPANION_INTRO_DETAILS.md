# Opening details — Companion 0.4.30

## Digital equation

The mini robot now shows a single compact line, without whitespace padding or a line break, in a dark cyan terminal panel with chamfered corners, soft scan lines, and highlighted corner strokes. Its width follows the measured text and fits the screen; typing stays on one baseline.

Displayed formula: `a′ⱼ=(1−d)aⱼ+(1−aⱼ)nⱼ⁺−(aⱼ+1)nⱼ⁻`.

This is the classic HOTCO activation update for bounds −1 and +1, written as one expression: a′ is the next cycle, n⁺ = max(net, 0), n⁻ = max(−net, 0). It is algebraically equivalent to the sign-dependent update in [Paul Thagard's technical appendix](https://watarts.uwaterloo.ca/~pthagard/Articles/Pages/oj.html). The equation is decorative; the backend continues running its existing continuous HOTCO-CT field with its own node bounds and availability mask.

## Logo and portrait

The purple logo plate is now a single immutable image, replacing the previous join between a cropped logo and a separately rounded left rectangle. The separately animated portrait uses 12 cells; its middle-row wink extends and retracts a rose-pink tongue. Lettering and the continuous purple frame remain static.

Assets created with the built-in image generation tool:

- `android-native/app/src/main/assets/art/jrpg/companion-intro-portrait-v2.png` (1448 × 1086, 4 × 3 poses).
- `android-native/app/src/main/assets/branding/companion-ovgu-plate-v3.png` (2173 × 724).

Exact prompts, references and original generated paths: `COMPANION_INTRO_DETAIL_PROMPTS.json`. Previous asset versions remain available. Read-only inspection confirmed genuine alpha transparency; portrait metadata follows the actual cell dimensions.

## Mushroom

The title's measured M glyph now anchors the mushroom landing. The mushroom drops from the puppy beside the C, settles upright on the M's cap, and triggers the existing title glow at narrative time 7.2 seconds.

## Delivery

Version code 41 / version 0.4.30. Built using `node scripts/build.mjs assembleDebug` and installed on the connected Samsung SM-A566B. Physical-device capture `.local-backups/companion-intro-0430.png` shows the single-line terminal equation, continuous purple plate, wink with pink tongue, and mushroom over the measured M. The landing height was then lowered 3 dp to touch the glyph cap. No automated tests were added or run for these visual changes.
