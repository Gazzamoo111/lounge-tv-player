# Lounge TV 2.0: source audit and first checkpoint

## Baselines inspected

- Lounge TV main: b163e75e9a53a2e3bec2bbc39d5129bd2c6e1cc2.
- Playback CI pins OwnTV_Core 1de78b1e2690c22b50c060e986bce221454bf880 and applies scripts/patch_lounge_playback_core.py. The Maven dependency alone is NOT the effective production/QA architecture.
- NuvioPlusIPTV dev: b348ce79fc4b6a038a3fff2a7b8da25cae181b29. Inspection is of source, not feature descriptions.
- Supabase read-only function inventory: lounge-core-xtream v50, lounge-core-live-xmltv v24, lounge-sports-candidates v9 and candidates-qa v17. Read deployed lounge-sports-candidates v9 source as well as inventory. No database, function, customer or gateway mutations made.

## Findings and decisions

| Reference implementation | Actual dependencies and behaviour | Lounge decision |
| --- | --- | --- |
| SportsMatcher.kt | EpgDatabase, SportsCatalog, SportsEvent, per-channel quality/settings, guide slot caches, token index and user fixes. Combines guide team evidence, event-channel names and ESPN network hints. | Preserve current full-inventory backend and core WatchMatcher. Network identity alone cannot establish the actual video content. Sports overhaul remains a later checkpoint. |
| SportsTvListings.kt | Parses TheSportsDB listings from Nuvio data JSON, joins teams and times within a three-hour window, adds regional broadcaster hints. | Useful discovery pattern, but listings and broadcaster hints are not playback verification. Do not add an undeclared runtime dependency on Nuvio's hosted data. |
| EpgDatabase.kt | SQLiteOpenHelper, indexed programme table, light projections, bounded channel chunks, separate full-text reads and sports scanning. | Keep Room/core GuideReader. Lounge already has bounded per-channel queries, source priorities, manual EPG mapping and time shifts. No second EPG database or replacement ingestion. |
| LiveTvPosterResolver.kt | AddonRepository, CatalogRepository, poster store, user add-on catalog searches, exact/close-title scoring, bounded caches and queued lookup permits. | Cannot drop into Lounge: dependencies belong to Nuvio's addon ecosystem. New strict resolver uses Lounge's registered MetadataProvider/TMDB configuration. No VOD activation or inventory required. |
| LiveTvPlaybackController.kt | Singleton Hilt controller, Media3, recording/timeshift helpers, bounded memory buffers, attach/detach player ownership, frame/stall checks, reconnect ladder and same-game failover hooks. | Preserve Lounge's existing LiveTuneController, MPV and LivePreviewEngine ladder. Existing Exo reuse should be measured before any replacement. No new decoder, stream probe or automatic feed switching in this checkpoint. |
| LiveTvOverlayMode.kt | Existing singleton playback underneath focus-controlled channel, guide, schedule and backup overlays. Lazy lists and remote-key handling. | Adopt the interaction principle: browsing a complete schedule must not acquire/release playback. Implement in Lounge's existing Stage components. |

Lounge already has timeline EPG, provider fallback, preview, reminders/recording, per-profile customisation and Saved. The Live page only exposed the next programme and one later programme. That is the first incremental improvement, not a rewrite of already working foundations.

## Implemented checkpoint

- Debug-only `LOUNGE_TV_2` build flag, default false. Only `LOUNGE_TV_2_QA=true` enables it and installs as `com.loungetv.player.v2qa`, alongside production and existing QA packages.
- Full channel schedule popup, day navigation to the configured guide retention horizon, full programme descriptions, retained row and scroll position across the existing programme-action menu.
- One channel/day read via existing GuideReader, preserving EPG source priority, manual assignments, profile customisations, offsets and provider fallback.
- Calendar-day boundaries use device timezone and local midnight, including 23/25-hour DST days. Existing sports NZ-time behaviour is unchanged.
- Preview session continues under the popup; all Watch/catch-up/reminder actions use the existing programme menu and tune paths.
- Selected-programme poster on schedule and now-playing poster on the Live pane. Exact normalized title/original title, strict supplied year and content type. Remakes and movie/series ambiguity fall back to channel logo. No popularity tie-break or broad fuzzy identification.
- Registered shared MetadataProvider; metadata-disabled setting respected. Background IO, 350 ms focus debounce, serial queue, 8-second lookup timeout, 256-entry disk cache. Positive cache seven days, confirmed misses six hours; transport failures are not negative-cached. Configuration digest includes metadata language/server. No key or credential is persisted in the cache.
- Existing QA workflow validates APK identity and signature, runs app/core/player unit suites first, and uploads `lounge-tv-2-v2qa`. PR builds use the repository's existing ephemeral signer without production secrets. Stable signing remains the existing manual QA workflow path.

## Sports evidence checkpoint

Deployed `lounge-sports-candidates` v9 queries catalogue names, applies team/title token overlap and known broadcaster rules, and emits `definite/likely/maybe`. It does not inspect EPG programme text or video content. Therefore v2 QA displays event-name evidence, broadcaster suggestions or unverified suggestions explicitly; even local EPG matches say content is unverified. The existing playing indicator means a channel is playing, not that its content matches the event.

QA rejects backend channel names explicitly advertising replays, highlights or classics. New evidence/replay unit cases cover unknown server confidence values and word boundaries. This is an accuracy safeguard, not the requested full multi-signal sports matcher. Existing full-inventory backend, curation, fixture discovery, user league/channel preferences and reminders are preserved. Backend transport cancellation now propagates instead of being swallowed.

## Licence review

Both repositories contain GPL v3 licence text. This checkpoint copies no Nuvio source, icons, imagery or hosted data. The source comparison informs independently written Lounge code; preserve existing OwnTV attribution and GPL licence. Distribution still requires the corresponding source and existing dependency notices, including applicable build/installation information. Metadata artwork follows the existing TMDB integration and attribution path; artwork provenance is not programme verification.

## Acceptance and rollback

This is milestone 1 groundwork, not the complete 2.0 release. No replacement playback engine, sports matching overhaul or new personalisation system is claimed. Disable the QA flag or uninstall v2qa to roll back; production data is separate. Do not merge or trigger publish.yml until the following pass:

1. App/core/player unit suites and isolated ARM APK assembly/signature/package verification.
2. Real Fire TV launch/login, catalogue ordering and all current VOD/profile/Saved/Sport Lounge regressions.
3. Open/close schedule while watching, held D-pad navigation, menu return focus, missing EPG, manual mapping/time shift, last guide day and offline recovery.
4. Compare startup, first frame, channel change, guide response and memory on identical streams/network/device. No performance numbers are claimed here.
5. Validate Sky Sport RAW feeds and long-running playback against wrong-content/replay/loop incidents. A correct EPG label does not prove stream content.

No physical Fire TV or emulator was accessible during this change. No playback, install, visual or performance acceptance can be claimed from source checks alone.


## Sports matching checkpoint 2

The QA application now reassesses merged backend/local suggestions against stored EPG programme titles, descriptions and fixture start times. It reads at most 512 candidate guide channels in chunks of 128 and fetches descriptions in batches of 400. Manual guide assignments and per-channel/global offsets are applied. No playback probes or extra provider streams are opened. Guide failure falls back to honest name-only suggestions; cancellation propagates.

Exact full/short team phrases require word boundaries and distinct aliases. Both teams and a nearby overlapping schedule establish EPG evidence. Explicit wrong opponents, replay/highlight names and contradictory overlapping listings are excluded. Missing or distant EPG never establishes guide evidence. A following fixture does not veto the game at kickoff. Non-team events require an exact sufficiently distinctive event title. Broadcaster hints remain suggestions. None of these states claims actual video-content verification.

Duplicate discoveries of one provider feed collapse into one choice, preferring its stored channel row so manual mappings remain effective. Existing hidden inventory remains searchable and the customer-facing catalogue is unchanged.

Hold OK on a QA sports feed opens report/use-for-competition actions. Negative reports are saved for one profile, fixture key, kickoff time and provider feed identity. They expire, are capped at 64 fixtures and 64 feeds per fixture, and can be restored from the match page. Persistence failures roll back the in-memory edit and show an error. No raw credentials or stream URLs are stored. The same resolver protects match-page choices, reminders and player alternatives. Profile changes invalidate an in-flight candidate search.

New tests cover evidence timing/contradictions, ambiguous aliases, EPG description matching, manual mapping/offsets, bounded guide queries, database failure/cancellation, feed deduplication, report persistence/profile isolation/expiry/restore and storage failures. Physical-device interaction and playback-content checks remain outstanding. EPG accuracy and full-team alias coverage are limitations; strict matching may leave more feeds unverified rather than guessing. This does not implement automatic cross-channel failover or claim faster playback.
