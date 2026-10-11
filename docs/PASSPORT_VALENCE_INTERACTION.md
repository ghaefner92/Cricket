# Passport valence interaction — 0.4.55

The four reported mode valences retain seven integer values −3 through +3.
Unanswered remains null. Each endpoint heart changes the value by one level,
starting from zero for an unanswered item. Hearts disable at their bounds.
The left heart is always broken, shrinking and changing color toward −3;
the right grows red toward +3. Redundant minus/plus actions were removed.
The crystal Slider, explicit neutral action, native PixelCard and SNES font remain.

## Four photographic-reference scenes

Only the scene receives the same animated frame, arrows and swipe navigation
as Availability. Its info action opens bilingual landmark information and
an external source link. Navigation never answers the valence question.

- Walk: Elbauenpark Jahrtausendturm, three visible spiral ramp runs alternating
  with hidden rear passages, smaller sprite depth as the character climbs.
- Bike: Wissenschaftshafen, water ripples, warehouse Denkfabrik and cranes.
- Tram: OVGU Universitätsbibliothek, readable OVGU sign, glass glints and rails.
- Car: Quedlinburg Marktplatz, Fachwerk houses and the ivy-covered Rathaus.

## Avatar and companion reactions

One sprite group combines exactly one avatar and its companion: robot/drone,
explorer/spider, creature/spirit, naturalist/puppy. Each avatar has a new transparent
1536×1024 sheet: four transport rows and six columns (two gait poses per emotional
state: disgust, neutral, joy). Both faces react to reported valence. Movement,
hops, wobble, reaction emphasis, smoke and floating intact/broken anime hearts
scale with the absolute rating. Two fully opaque authored poses alternate while
continuous transforms animate between render frames, avoiding ghosted duplicates.
These are JRPG illustrations rather than a claim of literal 64-bit rendering.

Backgrounds and sprite sheets were created with built-in image_gen and copied
unchanged into app assets. Only alpha bounding-box metadata was calculated. Each pose now has its own
horizontal bounds; six small neighbor fragments are explicitly excluded at
render time. Vertical bounds follow measured row separators.
Reference photographs are retained in output/valence-art/references and are
not bundled in the app. Full prompt set: VALENCE_ART_PROMPTS.json.

Reduced motion uses static, visible poses, facial expressions and hearts;
it disables ambient movement and particle travel. The shared scene clock
is lifecycle-aware. Atlas lookup is cached per appearance.

## Validation

23 JVM unit tests pass, including route bounds, ramp depth/occlusion and static
reduced-motion placement. Device tests cover all four avatars × four modes ×
seven reported values, one-level heart input, bounds, slider accessibility,
neutral selection, a single avatar scene with its companion, absence of obsolete
controls, navigation without recording a rating, landmark dialogs and German
font scale 1.3 at 320 dp. Synthetic drafts do not overwrite the user's Passport.
Screenshots: output/valence-art/device. Build/version: 0.4.55, code 66.

## Photographic references and factual sources

- Tower: [official photographic gallery](https://www.jahrtausendturm-magdeburg.de/galerie_turm_sanierung.html), [Elbauenpark information](https://www.elbauenpark.de/de/attraktion/jahrtausendturm/).
- Harbor: [OVGU harbor photographs](https://www.transport.ovgu.de/Freiraum%2BHafen.html), [Magdeburg science district](https://wissenschaft.magdeburg.de/Wissensstadt/Wissenschaftshafen/).
- Library: [OVGU reopening photograph](https://www.ovgu.de/-p-140526.html), [OVGU campus panorama](https://ovgu.pageflow.io/campusdateinternational), [library facts](https://ub.ovgu.de/ub/en/About%2Bus/Facts%2Band%2BFigures.html), [address](https://ub.ovgu.de/ub/en/Contact.html).
- Quedlinburg: [Harzspots photographic reference](https://harzspots.com/en/erkunden/unesco-welterbestaetten/spot/altstadt-quedlinburg/), [town hall](https://www.quedlinburg-info.de/en/sights/town-hall.html), [UNESCO information](https://www.unesco.de/kultur-und-natur/welterbe/welterbe-deutschland/stiftskirche-schloss-und-altstadt-von-quedlinburg).

## Scale and ambient refinement in 0.4.55

Reaction growth is limited to 3.5% instead of 20%. Base sprite group widths
are 13% of scene width on the tower, 30% on the harbor path and 44% for vehicles;
existing ramp depth reduces the walking figure further in the distance.
Positive hops are gentler (1.8 dp). Facial expressions and companions remain.
Clouds and birds move in all four backgrounds, with moving harbor reflections,
library glass glints, Quedlinburg flags and drifting leaves at the park/library.
Background bitmap geometry remains fixed so the tower route stays aligned.
All ambient effects are disabled with reduced motion.
Bicycle screenshots now sample both alternating poses for every avatar at ±3.

Atlas audit: `python android-native/scripts/inspect-valence-atlas.py`.
Validation screenshots for this refinement: `output/valence-art/device-0.4.55`.

## Tower rear turns in 0.4.56

Visible ramp runs still take eight seconds. Rear passages take 0.9 seconds
(instead of eight), with the fully hidden interval approximately 0.75 seconds.
The avatar and companion reappear together. Unit coverage bounds the hidden
interval below one second across repeated loops. Version code 67.

## Complete bicycles in 0.4.57 (code 68)

Some authored bicycle wheels extend beyond their nominal 256-pixel atlas cell.
Cell crops truncated them. Bicycle bounds are now derived from all six connected
silhouettes across the full row, including pixels across column boundaries.
A cached native clip path isolates each full silhouette; no neighboring wheel
or pet is composited. Source PNGs remain unchanged. The alpha silhouette is
expanded by two pixels to preserve antialiased edges. All 24 bicycle poses are
checked by inspect-valence-atlas.py, and both gait poses per avatar at ±3 are
captured on the phone. Earlier tower timing and ambient effects are preserved.

One naturalist bicycle pose was also clipped at the original PNG right edge.
It now uses its complete partner pose with the same positive expression; native
travel, hopping and particles still animate. No truncated wheel is rendered.
