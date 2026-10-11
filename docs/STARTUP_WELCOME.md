# Cricket 0.4.14 — startup welcome

Cold starts show CRICKET and all four existing companion avatars, joyful
sprite animations, waving vector hands and greeting bubbles. A loading
indicator and original institutional assets accompany the welcome. Motion
settings disable animated movement and use a stationary loading symbol.

The welcome lasts four seconds from its first composition. Its monotonic
deadline lives in the ViewModel, so rotation does not restart it. Migration
and initialization continue behind the welcome; their normal states appear
afterwards if unfinished. Returning to an existing app session does not replay
the welcome or delay foreground tracking. No journey records are modified.

Branding:

- `imiq-ovgu-logo.png`: existing project repository asset, copied unchanged.
- IMIQ project name appears explicitly below the combined institutional logo.
- `imiq-funding-official.png`: original funding graphic downloaded from
  https://www.imr.ovgu.de/niimo_media/Bilder/IMIQ/F%C3%B6rderer_kombiniert_DE-height-77-width-230.png
  and displayed unchanged on white, with its aspect ratio preserved.

Funding attribution was checked against the official IMIQ page:
https://www.imr.ovgu.de/IMIQ.html (EFRE) and the university announcement:
https://www.ovgu.de/Presse%2B_%2BMedien/Pressemitteilungen/PM%2B2025/Mai/PM%2B23_2025-p-142472.html
(European Union and Sachsen-Anhalt).

Android assembleDebug compilation is the build validation. No automated tests
or timed device interaction tests were requested or run for this change.
