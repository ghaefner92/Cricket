# Cricket 0.4.15 — dedicated welcome animation

Four new transparent 4 × 4 atlases each contain 16 authored greeting poses.
They replace the reused joyful atlas and detached vector waving hands only
on startup. Other companion scenes keep their existing artwork.

Robot: antenna/eyes activate, head tilts, arm raises and waves.
Explorer: steps forward, tips hat, waves and welcomes the journey.
Creature: crouches, hops, lands and waves both arms.
Naturalist: two dogs rise from behind the backpack and wave their front paws;
the red, white-spotted amanita at the backpack side illuminates softly.
The dogs remain with the naturalist, matching the existing character identity.

Playback uses authored cell anchors and 10 fps for the introduction and wave
cycle, followed by slower settling poses. Characters begin with a slight
stagger, a short entrance and a final fade. The last pose holds before the
four-second welcome ends. The ViewModel clock survives rotation. Motion-off
settings show greeting pose 10 with no movement.

Artwork is generated with the built-in image_gen tool using each original
character sheet as an identity reference. Prompts are recorded separately.
Original character sheets and institutional logos remain intact.

Assets are intended for the installed APK. Build validation uses
assembleDebug; no automated software tests are run for this change.
