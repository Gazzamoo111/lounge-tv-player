# Phase 1 production-upgrade contract

Baseline: released `v5.2.2.0` (`b2195d68`). This candidate does not merge PR #5 wholesale.

## Locked decisions

- Keep OwnTV's Home screen and player architecture.
- Prefer native Trending when it has at least four playable matches.
- Otherwise choose deterministic, profile-filtered playable catalogue fallbacks that do not require TMDB.
- For live-only sources, show a playable visible channel and its available logo/EPG data.
- Catalogue recovery is non-destructive, uses WorkManager `KEEP`, stays retryable, and refreshes Home after catalogue changes.
- Home loading, empty, and failure states must always be visible and actionable.
- Keep the approved original Lounge TV logo unchanged. Generate a square transparent native-splash asset with all visible pixels inside Android's safe circle.
- QA uses `com.loungetv.player.qa`, a persistent QA certificate, and monotonic build codes. Production stays `com.loungetv.player` and fails closed when a signed candidate lacks explicit release version inputs.

## Measurable acceptance criteria

1. Fresh profile with playable Movies/Series and no history renders a playable hero and catalogue rows within two seconds of the database becoming available.
2. Trending counts 0, 1, 2, and 3 use the deterministic catalogue fallback; 4 or more use Trending.
3. TMDB disabled, offline, malformed, or failed never leaves Home blank or unplayable.
4. Live-only content renders a live hero and live row; EPG/imagery enrich it when present and channel name/logo remain the fallback.
5. No playlist renders setup guidance. A feed exception renders an error with retry, and retry can recover.
6. A completed sync or restore updates Home while it remains selected. Recovery schedules only applicable sources, never force-prunes, and never interrupts playback.
7. Profile switching and restart show only the active profile's content. Existing Continue Watching, recent, favourites, settings, and history remain intact.
8. The native splash shows one centred, sharp, complete original logo at 720p, 1080p, and 4K on pre-31 and 31+ launch paths, with no circle, tagline, sound, duplicate intro, or clipped pixels.
9. Automated splash geometry proves every non-transparent pixel is inside the platform safe circle with at least two percent radial margin.
10. QA artifacts have a stable signer and increasing version codes. A production candidate has package `com.loungetv.player`, version code greater than `5020200`, and the same signer as the installed/released production app before any update is attempted.

## Evidence required before approval

- Focused unit tests for Home selection, loading/error/retry, recovery scope, splash geometry, version mapping, and build identity.
- Clean unit-test and standard release/QA build results.
- Before/after Home and splash captures from a rendered emulator or device.
- `aapt` package/version/ABI evidence and `apksigner` SHA-256 fingerprints for old and candidate APKs.
- No `adb install`, uninstall, clear-data, release, publish, or deployment before explicit approval.
