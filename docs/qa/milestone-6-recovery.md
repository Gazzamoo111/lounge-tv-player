# Milestone 6 recovery

## Baseline and what is established

Gary reported buffering, lip-sync drift and sluggish navigation on physical Fire Stick QA #610, commit `90c8770b7bdd3a4a077aa79c9507d4e7a602095d`. Earlier physical VOD was reported good; the exact earlier build, programme URL, codec and output route are not available in this workspace. Passing historical unit tests are not a substitute for that hardware baseline.

Both v5.2.2.11 and #610 declare OwnTV core 1.0.69 and Media3 1.11.1. The effective patched core was compared, not just the dependency version. The Milestone 1–5 changes to OwnTVPlayer concern recovery cancellation and HTTP refusal handling, rather than a new VOD buffer/decoder/audio-sync configuration. Milestone 6 keeps OwnTVPlayer, SmartBuffer, MpvLiveBuffer, ExoSubtitleEngine, AudioOutputPolicy and FilmWatchdog byte-for-byte equal to #610; CI records their SHA-256 values. No buffer increase, new engine default, timestamp invention or audio-offset workaround is introduced.

## Defects and targeted repairs

| Defect established in source | Repair | Evidence |
| --- | --- | --- |
| LiveViewModel's browse-preview cleanup called LiveTuneController.stop(), which unconditionally stopped the shared MPV engine even if a film owned it. | Separate browsing cleanup checks the controller's current session URL before stopping MPV. It still cancels Live recovery and releases the Live Exo decoder. Explicit whole-player stop remains available. | Identical controller cases run against effective #610 and the repair. Unrelated VOD stop commands must change from 1 to 0; a Live-owned MPV session must still receive 1 stop. |
| SportsViewModel's lifetime reminder reconciliation retained Hub and discovery subscriptions after the UI left Sport Lounge. OnNow work could also outlive the visible screen. | The shell supplies actual foreground visibility. Hub, discovery and OnNow polling cancel while another section or full-screen playback is active, then resume on return. Stored reminders and profile preferences are retained. | A cancellable polling-repository test records five inactive baseline polls versus zero gated polls over the same simulated interval, and checks cancellation/restart. These are controlled fake-repository counts, not measured provider traffic. |
| Home's fixed order put movie Trending first. Sports were only a lower rail, with a 90-minute upcoming window that excluded many major fixtures. | A bounded sports-first carousel uses the existing fixture pool, event cards, source team logos/colours and reminder path. It considers live events and the next 72 hours, ranks major/followed events, deduplicates and excludes stale/completed/cancelled entries. No fixture implies stream availability. | Pure ranking tests plus a rendered Home carousel test exercise navigation, event and reminder actions. Existing Home fallback remains when no suitable fixtures exist. |
| Detail parsing used top-level poster_path even though TMDB image alternatives were requested. Provider posters won in grid/detail presentation. In-memory metadata could survive profile/language changes. | Image-only English → textless → original-identity poster policy, choosing highest resolution in the preferred tier. Synopsis language remains untouched. Versioned image cache keys invalidate old poster decisions; profile/config changes invalidate memory caches. Verified cached TMDB artwork takes precedence over provider posters. | Poster-language/fallback/cache-key tests. Grid probes are cache-only, limited to 48 visible entries after settling, with bounded positive/negative in-session bookkeeping. Only a settled selected title resolves metadata over the network. |
| Focus metadata resolution resumed matching/parsing on the view-model main context. | Resolve selected movie/show metadata on IO and detail JSON parsing on Default. Preserve debounce and cancellation. | Production poster-component D-pad test records instrumentation input-to-idle timings. Native playback probes report CPU/PSS under a labelled synthetic workload. |
| The section title still read Sports. | Override customer-facing section resources with Sport Lounge. Preserve My Sports, IDs, settings keys, routes and APIs. | Rendered resource assertions and the unchanged Milestone 4/5 navigation tests. |

## Performance evidence and limits

The regression artifact contains the original four playback comparisons, the two #610 ownership comparisons and `m6-vod-configuration-invariants.json`. The rendered-UI artifact contains `m6-results/navigation.json` and one `vod-*.json` per actual playback engine, alongside labelled screenshots.

The native emulator probes play a locally generated 640×360, 30 fps H.264/AAC MP4 through OwnTVPlayer's Exo-only and MPV-only paths. They check position advance, engine identity, seeking and overlay updates without a session reset; record time to a playing position, buffering transitions, process CPU and PSS. This is a synthetic local-file playback check. It is not a BEST network-stream test, hardware-decoder throughput measurement, first-rendered-frame measurement or proof of corrected lip-sync. Output A/V drift and dropped-frame counts are not exposed by the current probe and are not reported as measured.

The navigation figures include instrumentation scheduling and idle synchronization. They are not raw remote-to-display latency, and there is no matched physical before/after navigation trace.

The ownership and unnecessary polling defects are verified engineering targets. Their contribution to Gary's buffering and lip-sync symptoms remains a hardware hypothesis until the same real content is retested. The changes must not be described as a measured Fire Stick buffering/lip-sync fix solely because automated checks pass.

## Physical Fire Stick acceptance

1. Update over #610 without uninstalling; retain profiles, authentication, favourites and playback preferences.
2. On the same network and audio output, play the same movie and episode used for the regression report. Record engine, resolution/codecs, startup, stalls and lip-sync at start, after seeking and after at least 30 minutes. Repeat with the other configured engine. Do not compensate with a new audio delay during comparison.
3. Visit Sport Lounge first, preview a feed, then play a movie. Confirm the old feed releases and movie playback survives opening/closing controls and subsequent navigation.
4. Browse Movies and Series rapidly while posters resolve; compare focus response, category transition and poster quality. Check English artwork without changing synopsis language.
5. Check Home live/upcoming fixtures, local times, carousel, reminder and event routing. Check Sport Lounge naming, My Sports, preview sound, feed picker, full-screen return, EPG, Recently Played and Sky Sport NZ RAW.

## Native playback CI diagnosis (QA #614)

QA #614 completed with 2,040 Kotlin tests, all six controlled comparison cases, ten existing Sports UI tests and the restart persistence test passing. The four-case Milestone 6 emulator group passed Home, D-pad navigation and ExoPlayer local playback, but failed its MPV-only local MP4 probe with `FileCorrupt`. The emulator logcat establishes the immediate cause: `File.toURI().toString()` supplied `file:/data/user/0/.../m6-local-av.mp4`, which libmpv interpreted as `/file:/data/user/0/.../m6-local-av.mp4` rather than the existing local file.

The instrumentation probe now passes `media.absolutePath` to `OwnTVPlayer.play()` for **both** engines and asserts that the generated MP4 exists and is nonempty before playback. This is a **test-harness path fix only**, not a change to customer playback engines, buffers, audio offsets or codecs. The corrected test still requires a successful CI replay before declaring the signed APK ready. QA #615 did not schedule jobs (the run concluded with no jobs or artifacts), so it does not establish either success or failure of the probe. Physical Fire Stick lip-sync and network-stream buffering remain open acceptance checks.

## Physical Fire Stick QA #616 findings (10 November 2026)

The owner installed QA #616 on the Fire Stick at `192.168.68.50` and reported four unresolved observations:

1. **Zootopia:** foreign-language poster remains. The app's visual precedence can still fall back to the provider poster when no English TMDB metadata is available or matched. This **is not fixed** by the sports patch; obtain selected-title TMDB match/cache evidence before changing title matching or initiating uncontrolled network prefetches.
2. **Ocean's Eleven:** lip-sync drift remains and is worse on MPV. The device, stream codec, audio route, selected track and stream timestamps have not been captured. Do not apply an arbitrary delay, increase buffers or declare native local-file emulator playback proof of real stream sync. Retest with Exo first and collect a real-device playback trace before changing decoder/audio policy.
3. **Sports event times:** Home and Sport Lounge / match UI did not agree; some event times appeared in US Eastern time. All QA-only Home carousel, Sport Lounge cards and match page kickoff displays now use the same `Pacific/Auckland` formatters, including NZST/NZDT conversion. Kotlin daylight-saving assertions added. Requires Fire Stick verification.
4. **Tennis:** opening a Sport Lounge card blinked with no usable feed, although the subscriber could find sporting streams in Live TV. The card's explicit search was local-only while the match page also queried backend candidates. Both now use the same parallel backend/local fixture discovery and the same evidence/correction gates. The card displays search errors or no-verified-feed states with retry; the Home event action opens the match page and launches explicit channel discovery. This **does not guarantee** a matching provider channel exists, that an event is on a selected channel, or that the Supabase stream endpoint is healthy. Real tennis fixture and provider playback acceptance is mandatory.

**Release gate:** Do not merge PR #68 or present Lounge TV 2.0 as production-ready until the foreign-poster and Ocean's Eleven sync symptoms have reproducible diagnoses and on-device fixes, and the tennis/time corrections pass physical QA.
