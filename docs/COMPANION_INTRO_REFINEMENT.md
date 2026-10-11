# Companion opening scene — 0.4.28

## Behaviour

The opening scene stays visible until Let's go is pressed. The button is shown only when model.ready is true (local data/migration and the main UI are ready), without an extra timer delay. No automatic dismissal is scheduled. A migration failure exposes Retry import rather than leaving an indefinite loading screen. Existing ViewModel timing survives rotation; frame callbacks stop while the activity is in the background.

The narrative runs at 75% of the former speed, yielding about twelve seconds followed by a quiet idle. Avatars breathe and blink; puppies breathe and alternate seated tail/head poses. Entry is synchronous for all four characters. Fifth-degree easing removes sudden starts/stops. Short eased frame blends soften character and dog pose transitions; the second dog's turn is interpolated.

## Layout

All four avatars now draw their full source frame on the same full-screen canvas, enlarged and leaning inward at ±43 degrees. There is no source crop at the waist and no individual sprite canvas that clips the image; only the screen clips bodies extending beyond its sides. Lower avatars are positioned farther up to keep the footer clear.

The title uses measured first/last letter positions for the seated puppies. Naturalist hand/backpack coordinates share the same full-body rotation/translation as her sprite. At the mushroom's landing (original narrative time 7.2 s, approximately 9.6 real seconds), the title has a smooth Gaussian light pulse centered on impact and then settles into a brighter steady glow.

The white institutional card is removed. A compact transparent footer sits at the bottom center, up to 185 dp wide. The university mark is adapted for dark-background readability; transparent outer padding is ignored in its Canvas renderer. Funding uses the unchanged original official symbols, with its exact caption typeset in small ivory sans serif text. This keeps the caption legible without recoloring official flags.

## Art provenance

The built-in image_gen.imagegen tool produced two transparent logo adaptations. [Exact prompts and paths](COMPANION_INTRO_BRANDING_PROMPTS.json):

- Used: `android-native/app/src/main/assets/branding/companion-ovgu-transparent-v2.png`.
- Saved variant: `android-native/app/src/main/assets/branding/companion-funding-transparent-v2.png`. This variant was not selected for UI use after inspection; the original official funding symbols and exact native caption are rendered instead.

No character PNG pixels were changed. No logo originals were overwritten. Existing application ID, Passport/history and database identifiers remain compatible.

## Release checks

APK assembled successfully with node scripts/build.mjs assembleDebug and replacement installation on phone RZCY70K63HV succeeded. Installed package reports versionName 0.4.28 / versionCode 39, USB reverse tcp:8077 is restored and backend health returned ok. An initial physical-device screenshot exposed an explorer hand touching the footer; the lower characters were moved up and the funding caption compacted before final rebuild/reinstallation. The final physical-device screenshot shows the smaller transparent footer clear of the characters, the persistent idle scene and Let's go. It is saved in `.local-backups/companion-intro-0428-final.png`. No automated tests were run for this presentation change. The full moving sequence still needs user review.
