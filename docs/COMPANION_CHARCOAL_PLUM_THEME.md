# Companion charcoal/plum palette — 0.4.31

The shared native Compose theme now uses warm charcoal and muted plum surfaces.

| Role | Color |
| --- | --- |
| Screen background / Ink | `#19151F` |
| Surface / Panel | `#27212F` |
| Raised surface / card gradient top | `#35303D` |
| Decorative lavender borders / Edge | `#756B82` |
| Secondary text / MutedText | `#B6ACBE` |
| Primary ivory text / Pale | `#F4EFE7` |
| Primary action / Mint | `#79D6B5` |
| Recommendation / Gold | `#FFDA83` |

`PixelUi.kt` defines all shared tokens and Material color roles, including dialogs, text fields, navigation indicators, sheet surfaces and inverse colors. Secondary text is separate from decorative borders so small labels do not inherit the dark outline shade. Shared cards use a raised-plum to panel gradient. Neutral circular mode controls use the same raised surface; their selected state has a muted mint tint and recommended state retains gold.

The welcome screen uses the shared charcoal/plum gradient plus a faint static plum light centered behind the title. Route-search loading inherits the shared gradient. Home, onboarding, Passport, travel controls and settings inherit the shared theme.

Existing raster scenes, sprites, university logo, terminal panel, route colors and OpenStreetMap tiles retain their artwork colors. No bitmap generation is needed for this code-native theme change.

Release: versionCode 42, versionName 0.4.31. Build: `node scripts/build.mjs assembleDebug` completed successfully. Installed on the connected Samsung SM-A566B; physical screenshots of the opening and the current everyday-mobility step were inspected (`.local-backups/companion-theme-0431.png`, `.local-backups/companion-theme-mobility-0431.png`). Backend health returned `ok`; USB port 8077 reverse restored. No automated tests added or run.
