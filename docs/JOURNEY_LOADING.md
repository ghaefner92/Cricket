# Cricket 0.4.9 — full-screen route search

Route searches have a distinct operation flag, separate from passport loading.
While searching, app navigation, trip banners and notices are hidden and a
full-screen companion scene replaces the form. The existing reflective avatar
animation, orbiting particles and pulsing thought dots use frame timestamps.
Motion-disabled settings render the scene stationary.

First-person English/German messages invite the user to wait. Messages rotate
every 6.5 seconds; after approximately 30 seconds a patient follow-up appears.
There is no fabricated progress percentage or claim of a completed phase.
Cancel and Android Back use the existing cancellation mechanism. Success,
failure and cancellation release the search flag; the operation version keeps
an earlier cancelled job from clearing a newer search's state.

The content scrolls on short screens or with large text. No sensors, location
transmission or backend recommendation rules are changed by this loading UI.
Validation performed: Android assembleDebug compilation. No automated tests.
