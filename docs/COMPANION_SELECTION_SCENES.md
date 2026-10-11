# Companion selection scenes — Cricket 0.4.24

Meet your companion and the companion settings editor share one scene selector. Left/right 48 dp arrows wrap across four appearances; horizontal drags over 48 dp switch once per gesture. The selected character name, setting and four position dots sit beneath the scene. Name entry remains below; name and selection survive recreation with rememberSaveable. Save/Next still commits through saveCompanion.

Four new background images and four dedicated 4×4 action atlases are generated with the built-in imagegen tool, using existing character sheets as identity/style references. Existing welcome, journey and transport sprites remain their own separate assets.

## Assets

All assets are in `android-native/app/src/main/assets/art/jrpg/`:

- `meet-naturalist-background-v1.png`, `meet-naturalist-action-v1.png`
- `meet-robot-background-v1.png`, `meet-robot-action-v1.png`
- `meet-explorer-background-v1.png`, `meet-explorer-action-v1.png`
- `meet-creature-background-v1.png`, `meet-creature-action-v1.png`

Exact generation prompts are recorded in `COMPANION_SELECTION_PROMPTS.json`. Action atlases request genuine alpha transparency; backgrounds are opaque. Generated files are copied into the workspace without deleting their generation originals.

## Motion

- Naturalist: sunny Harz-inspired forest, mountain landmark and steam train, mushrooms and ferns; bends to collect a mushroom and puts it into a basket, with dogs by her backpack.
- Robot: futuristic city with cyan/violet neon; route hologram, antenna glow and friendly drone interaction.
- Explorer: castle/catacomb passage; map, lantern and friendly spider interaction.
- Creature: moonlit fantasy cemetery; friendly ghost and playful dance.

Each action atlas is displayed chronologically at three poses per second inside an eight-second loop, with pauses at the first and last poses. Background movement is native Compose drawing: drifting leaves and sunlight, a passing hover vehicle and neon shimmer, hanging spiders, or mist and glowing particles. Scene transition is a 420 ms crossfade. Reduced/off motion shows static scenery and the initial character pose without crossfade. Frame loops stop when the activity is in the background.

Read-only alpha inspection records actual row separators, frame rectangles and opaque central-body foot anchors in `meet-atlas.json`. Rendering uses this metadata to avoid adjacent-row clipping and stabilize the character on the ground without changing generated bitmap pixels. Artwork uses the existing shared 32 MiB bitmap cache and is not decoded during animation frames. Inspection results are in `COMPANION_SELECTION_ASSETS.json`.

Validation: APK compilation and visual inspection of generated assets. No automated tests were requested for this UI/art change.
