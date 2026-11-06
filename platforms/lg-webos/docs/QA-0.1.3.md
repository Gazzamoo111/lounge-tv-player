# QA 0.1.3

## Report and confirmed causes

User reports: Live now holds for 15 minutes; Recess 24/7 takes 6–10 seconds to start; guide entries missing; VOD navigation/selection stalls for about five seconds; Movies/Series focus Search; Settings is incomplete; Sport Lounge has two sports.

Inspection confirmed all three catalogue libraries were already downloaded behind startup. The remaining VOD work included focus-triggered metadata/artwork, original-sized TMDB decoding, linear Saved/progress lookup per card and full episode-season DOM creation. Movies/Series focused their Search input first. Visible Live rows only read the local guide cache; a miss did not fetch a guide until focused. Sport Lounge only queried the supplemental calendar instead of Android's main discovery feeds.

## Changes and limits

VOD defaults to local catalogue browsing, without metadata/backdrop requests on focus. TMDB images are bounded to w342 posters and w780 backdrops; the first browsing windows are warmed within a 1.8-second image budget. Virtual poster windows batch DOM additions. Saved/progress lookups are indexed. Movies expose Play/Save before metadata returns and enrich without stealing focus. Seasons are virtualised, with remote access to later episodes. This does not download every episode/metadata response before Home or claim measured physical LG speed.

Guide matching retains declared IDs, canonical ltv aliases, exact unambiguous display names and explicit overrides. No fuzzy matching. Visible rows fetch missing entries with two concurrent requests and negative-cache empty responses for one minute. Existing canonical short JSON provides a fast fallback, then the existing authenticated guide API. XMLTV sources are merged off the UI thread, with canonical overlap priority, atomic completed-source publication and source-failure counts. Extra feeds require HTTPS/CORS; gzip uses DecompressionStream where present. Missing upstream programmes are not fabricated. The public canonical feed inspected read-only contained 1,187 channel declarations and 51,108 programme records, which is not a promise of current coverage for every customer channel.

Settings reproduces the twelve source groups and imports 110 Android row definitions, with Search, help and Quick pins. Implemented controls include playlist login/add/switch/refresh, XMLTV source management and exact matching, guide time shift/horizon/refresh interval, profile add/rename/switch/delete, local backup/restore, Saved/history, accent/text size/motion, VOD columns, metadata/backdrops/ratings, live preview/sound/numbers, aspect/volume/track preferences/seek/resume/autoplay, retry/tune limits, startup destination and local reminders/diagnostics. Android-only/unported rows remain labelled unavailable. Kids/PIN, advanced layout/theme controls, external players, recording, device DNS/proxy and complete subtitle tooling are not complete. No claim of exact working Settings parity.

Sports discovery follows Android's 31 feed definitions and uses bounded concurrency, seven NZ dates, nested tennis/F1/MMA/cricket parsing and deduplicated supplemental calendar enrichment. Discovery changes are client-only; provider catalogue, event-channel resolver and confidence labels are unchanged. Feed availability is seasonal and can fail.

Native playback retains 0.1.2 continuity/recovery/fullscreen geometry. Duplicate blank decoder resets during channel changes are removed. First decoded-frame timing is logged when supported. Native buffering/GOP/provider response time remains outside this measured workspace; 6–10-second startup has not been physically remeasured.

## Validation

41 domain/native lifecycle tests pass. 28 browser journeys pass across regression and targeted final runs, including sixfold CPU throttling, 60 rapid VOD moves with the event loop advancing, no focus metadata requests, immediate movie Play before delayed metadata, a bounded 600-episode season, twelve Settings groups/help/search/preferences, XMLTV source actions, real worker source merge with canonical overlap priority and broader sports population. The CPU-throttled browser is not an LG measurement. Synthetic media events do not establish decoding.

TypeScript build and the actual IPK archive/identity/assets/no-bundled-fixture-credentials checks pass. No Android source, production backend, account records, catalogue curation, EPG pipeline or sports matching is changed or deployed. Follow DEVICE-QA.md for physical acceptance.
