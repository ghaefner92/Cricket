# Transit route preview starts on foot — 0.4.33

The A origin marker in `NativeJourneyMap` now uses the selected companion's walking sprite when the selected route is public transport. This applies to the route comparison / Choose journey view and its selected journey preview.

The overall route mode still identifies public transport in the mode selector, recommendation and geometry. During GPS recording, the existing `JourneyAvatarMotion` inference continues starting on foot and switches to public transport when sustained movement evidence supports boarding. Walking, cycling and car previews retain their corresponding mode sprites.

VersionCode 44 / versionName 0.4.33. Build: `node scripts/build.mjs assembleDebug` completed successfully. Installed on the connected Samsung SM-A566B; package version confirmed and USB port 8077 reverse restored. No automated tests added or run for this presentation fix.
