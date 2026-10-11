# Cricket 0.4.12 — compact Travel search

The Search view removes the app title bar, redundant search navigation row,
large heading and disclosure header. Its decorative magnifier is 8 dp instead
of 32 dp; input fields retain readable text and controls retain touch targets.

Location, Swap, Recent places and Journey preferences occupy one icon-only
row, with spoken labels and selected states. Recent places and preferences
remain collapsed until tapped. Swap updates both selected places and text
drafts, including empty fields. Controls are disabled during busy operations.

Each nonempty address field has a clear action beside its search action.
Clearing removes that field's selected place, text, suggestions and error;
the other field and saved recent places are preserved. Clearing or editing a
selected address invalidates its previous route result through setPlace.

Android assembleDebug compiled. No automated tests run for this change.
