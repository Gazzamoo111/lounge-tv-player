# Playback reliability QA, 29 October 2026

Baseline: production 5.2.3.1 (`fe7de5a`). This branch is not authorised for production promotion.

The existing immutable Core 1.0.69 patcher now applies an additional reviewed patch. It cancels tune-owned delayed reconnect, session-release and decoder-rebuild callbacks, checks tune/player identity and prevents obsolete work from changing a replacement tune. Unit tests use an injected clock/scheduler, including callbacks dequeued before cancellation, A→B→A, replaced players and fast-tracked/naturally recovered reconnects.

The credential changes selectively backport OwnTV_Core PR 54 (`ff3bc530142ea37827cab31e8e005cd18ee71861`), including URL encoding, catch-up conversion and upstream tests. Error-body scrubbing additionally covers short Xtream paths and decoded secrets before truncation. No database version, buffer size, engine default, account or customer setting changes.

`playback-tests.yml` runs core, player-core and app tests without signing credentials. Both the ordinary QA build and production candidate require this job. The candidate checks out the triggering SHA. Missing persistent QA signing material may still block stable QA packaging, but cannot prevent unit tests from running independently. PR QA builds use `com.loungetv.player.auditqa` with an ephemeral debug certificate and cannot update the production package.

## Before installing

Record run URL, source SHA, APK SHA-256, package, versionCode and certificate. Confirm the APK has the isolated auditqa package. Use the same approved provider account sequentially across players, with no competing preview/session. Never uninstall, clear or overwrite `com.loungetv.player`.

## Required physical-device validation

1. 100 channel changes, including rapid focus, A→B→A and preview promotion to fullscreen.
2. Switch channels during a gateway/session-release wait and during decoder recreation. No delayed restart, orphan sound/decoder or extra provider slot may occur.
3. Brief and extended internet loss on a dedicated test connection, then restored playback. Observe whether recovery happens naturally or through the reconnect ladder.
4. Clear/protected channel transitions where applicable, UHD→HD, sleep/wake, favourites, profiles, EPG and NZ/AU category visibility.
5. Full movie with seeking/resume and consecutive episodes.
6. Four-hour live run and an eight-hour endurance run, compared with production at equivalent settings. Record rebuffer count/duration, recovery time and memory growth.
7. Inspect exported diagnostic text for credential leakage using test credentials, including reserved characters, short paths and signed query tokens.

Passing compilation and JVM tests only makes the APK ready for these tests. No production merge, version bump, release, download redirect or customer account change is part of this branch.
