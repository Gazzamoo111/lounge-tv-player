# Research and read-only baseline

Observed 2026-11-05 Pacific/Auckland. Read-only source and production metadata inspection; no stream opened, test suite run, build, installation, deployment or production mutation.

## Source identity
Repository: https://github.com/Gazzamoo111/lounge-tv-player

| Record | Observed identity | Meaning |
| --- | --- | --- |
| Main / initial local checkout | c24affa6301ff83bbdafaf562d83a499d82c374c; local chore/adopt-spec-kit | Stale for current sports work; untracked .agents/, .specify/, supabase/ preserved |
| Current sports development branch | feature/lounge-major-events-home-20261103 at d081d77cbec6f5f498a16c58250d96f440842c90 | GitHub compare reports 103 commits ahead of main, zero behind |
| Shared Spec Kit integration | chore/spec-kit-gpt-work-integration-20261104 at ff69a7a3147c37b6ab3be93e7e7a78bae95f527d | One documentation/tooling commit above sports development; clean local checkout observed |
| Latest production APK / installed package | Not independently inspected this session | Release branch names and build comments do not prove installed identity |
| Deployed candidates function | ACTIVE v9; deployment bundle SHA256 bbd492266a8a615119a4180f1214c3a0e4ba2c0b2b732785ef50b394f3e19f03 | Retrieved deployed source via read-only connector; verify_jwt=false. No claim that client authentication or abuse protection is adequate |

GitHub comparisons were queried live, rather than inferring branch state from local refs. References were not fetched into or merged with the original repository. No branch reconciliation is part of this delivery. Unpushed GPT Work changes remain unobservable.

Source anchors below are repository-relative under `app/src/main/java/tv/own/owntv/` unless explicitly qualified. Read immutable [development source](https://github.com/Gazzamoo111/lounge-tv-player/tree/d081d77cbec6f5f498a16c58250d96f440842c90) or [workflow source](https://github.com/Gazzamoo111/lounge-tv-player/tree/ff69a7a3147c37b6ab3be93e7e7a78bae95f527d).

## Findings and decisions

| Evidence anchor | Finding | Decision / alternative rejected |
| --- | --- | --- |
| features/sports/BackendSportsCandidates.kt:25-33 | Request lacks event identity, time and regional broadcaster evidence | Versioned evidence contract; reject client-only score tweaks as insufficient |
| Same :48-72; BackendSportsCandidatesTest.kt:21-42 under app/src/test/java/tv/own/owntv/features/sports/ | Confidence becomes GUIDE/EVENT/LIKELY; existing tests enshrine authority | Replace authority assertions with evidence checks, preserve unavailable-backend fallback |
| Same :87-113 | Nonempty backend suppresses local LIKELY alternatives | Evidence-ranked source merge with stable service identity; preserve valid local alternatives |
| backend/supabase/functions/lounge-sports-candidates/index.ts:110-167 | Catalogue rows carry channel metadata, not programme/playback evidence; both-team names become definite, generic networks become likely/maybe; ID-only dedupe and unbounded output | Extract deterministic matcher, add fixture/time evidence, caps and logical-service grouping |
| Same :70 | SKY SPORT prefix guard also captures UK SKY SPORTS and requires NZ | Typed regional service matching; retain AU Main Event versus UK Sky Sports Main Event guard |
| Same :92-99 | FOOTBALL branch can match NFL before SOCCER | Explicit sport taxonomy using competition context; no ambiguous substring branch |
| Same :68 | Seven regex contains literal escaped word-boundary sequence | Include AU Channel7/7plus regression; avoid unrelated broad refactor |
| Deployed v9 versus checked-in :27 | Fixture separator regex differs | No byte-parity claim. Capture a normalized diff of deployed/source before future implementation; deployed v9 is original-defect regression baseline |
| features/live/LoungeLiveSections.kt:31-37,88-92 | Sports · Soccer already renders as Sports section and Soccer folder | Reuse existing section semantics; do not introduce competing navigation |
| features/live/LoungeLiveCatalogueCleanup.kt:34-110 | Many event/PPV folders intentionally hidden | Curate eligible Soccer items; do not globally unhide event folders |
| features/live/LoungeLiveDuplicates.kt:26-64,79-86 | Kayo dedupe has specific 502 typo rule; 501 CRICKET/504 FOTTY differ; EPG presence influences quality preference | Explicit service identity for affected backups; EPG presence is not reliability proof |
| features/live/ZapTuneDelay.kt:9-35; LiveViewModel.kt:1632-1650 | 1500ms isolated/500ms rapid delay, cancellation and ZapTiming already present | Extend trace and cancellation evidence; reject removing debounce or speculative parallel probes |
| player/MpvVideoSurface.kt:132-135 | Intentional mpv->Exo freezeFrame overlay until first frame | Generation-bound frame ownership and visual test; do not assume READY proves frame replacement |
| gradle/libs.versions.toml:82 | core/player-core pinned 1.0.69 | Inspect exact resolved source and applied playback patch later; preparation internals not all in app tree |
| features/home/MajorEventSelection.kt:21-43,91-112 | All IN accepted; additive live boost and variety pass can put upcoming before live | Strict live/upcoming partitions, bounded status freshness; preserve editorial variety |
| features/sports/SportsViewModel.kt:247-255 | Hero depends on data emissions | Independent clock expiry and visible refresh |
| features/home/MajorEventsHero.kt:79-103,143 | Index selection; 10-second rotation; LIVE badge from IN | Stable-key selection, freshness validation, preserve safe View event action. Existing Watch Live defect is not asserted |

## Current BEST catalogue audit

Read-only SQL metadata reads covered `lounge_sync.best_inventory_items`, `best_categories`, `best_inventory_imports`, selector records, `public.lounge_curator_channels`, and bounded joins of `lounge_core.live_epg_state` / `live_epg_programmes`. No credential-bearing payload or playback URLs exported.

Snapshot observed at 2026-11-04T19:14:42Z: 16,140 present live records; latest seen 2026-11-02T05:18Z. Latest import145 was still processing since November2; import141 failed; most recent complete import138 finished 2026-11-01T12:35Z. These are audit-time values, not a guarantee they remain current. Refresh health is an implementation prerequisite; do not conceal it with a new UI cache.

| Service | BEST ID | Catalogue guide ID | Current EPG observation |
| --- | --- | --- | --- |
| AU beIN1 | 646977 | beINSports1.au | No state row in bounded lookup |
| AU beIN2 | 646976 | beINSports2.au | No state row |
| AU beIN3 | 646975 | beINSports3.au | No state row |
| UK-labelled beIN AU1/AU2/AU3 | 1348093 / 1348102 / 1348103 | null for each | No state rows |
| AU Fox501 | 646971 | null | No state row |
| AU Fox502 | 646970 | null | Explicit empty, zero retained/current programmes |
| AU Fox503 | 646969 | FoxSports3.au | Explicit empty, zero programmes |
| AU Fox504 | 646968 | null | Explicit empty, zero programmes |
| AU Fox505 | 646967 | FoxSports5.au | Explicit empty, zero programmes |
| AU Fox506 | 646966 | FoxSports6.au | Explicit empty, zero programmes |
| AU Fox507 | 646965 | null | Explicit empty, zero programmes |
| AU ESPN1 / ESPN2 | 646974 / 646973 | null / null | Explicit empty for both |
| Kayo ESPN1 / ESPN2 | 1520168 / 1520167 | null / null | No state rows |
| Kayo501 CRICKET / 502 LEAUGE / 503 / 504 FOTTY | 1520176 / 1520171 / 1520175 / 1520170 | null for each | No state rows |
| Kayo505 / 506 / 507 | 1520174 / 1520169 / 1520173 | FoxSports5.au / FoxSports6.au / null | No state rows |

Explicit empty states above last synced around 2026-11-01T10:42-10:43Z. No state, empty state and missing catalogue guide ID are three different findings. None proves that video content is absent.

Curator view marked AU beIN1/2/3, UK-labelled AU beIN1/2/3, Fox501-507, AU ESPN1/2 and Kayo ESPN1/2 visible and present. Selector state for AU targets was suggested/untested with null ratings. Backend visibility is not proof of Android visibility or successful playback. AU ESPN1 still being backend-visible makes cross-surface suppression necessary while preserving audit records.

Additional UK-labelled variants: 238265 beIN1 HD maps to beINSports5En.qa; 238264 beIN2 HD to beINSports4En.qa; 238266 beIN3 HD to beINSports6En.qa; 735945 beIN2 EN SD to beINSports3En.qa; 735944 beIN3 EN SD to beINSports1En.qa; EN1 IDs483921/735946 to BeinSports1EnEn.qa. These are name/guide inconsistencies only. Qatar guide suffixes and UK provider folder names do not prove actual broadcast identity. Record on-screen channel bug and programme before proposing a remap.

Soccer-related current category counts: US Soccer PPV2436=201, Soccer Replay1322=108, EPL1308=34, EPL+PPV2528=10, LaLiga1591=20, SerieA1590=20, Ligue1 1982=10, Scottish1307=18, Cymru2511=16. Wholesale admission is explicitly rejected.

Historical local `QA/Reports/LoungeTV-playback-report.txt:150-162,260-272` shows Fox502 opening around three seconds on October28, followed elsewhere by loading/session contention and black.ts redirects. Historical evidence cannot establish present buffering or quality.

## Uncertainties and safety findings
- Actual stream content, current playback health, motion/audio, measured quality, target-device visibility and installed production APK identity remain unverified. These are mandatory later audit tasks, not product decisions filled by guesswork.
- Private-schema table metadata reports RLS disabled on eight catalogue tables. Exposure to API roles was not independently established; the tool's generic exposure warning is insufficient to prove grants/schema exposure. Record and inspect exposure/grants separately before any security change; no remediation or production configuration change is authorized here.
- Spec Kit 1.3.0 specify/plan instructions and active templates were read. No extensions.yml exists, so no before/after hooks were registered. To preserve the original checkouts, helpers/templates/constitution were staged in local work/, composed spec-template resolution and setup-plan.sh ran against the explicit output feature directory, and the feature pointer was confined to staging. No original repository pointer or branch changed. 001 is the first unused number in the inspected specs directory; GPT Work must recheck reservation before publication.
- No external product/version recommendation was required. The plan preserves inspected dependencies, rather than speculating about upgrades.
