# LG parity and source audit

## Source decision

- Latest published, non-draft release inspected: `v5.2.3.10`, commit `44388fa8683df20a51ad5166f3ea73d7b08c8051`, published 4 November 2026.
- `main` at `53649eed52587a7c372c5d1c6fe9ac2286c774ec`: recent docs/EPG work, but lacks 41 files/changes already in the published release, including Major Events hero and Saved. Do not base the client experience on main's app directory.
- `feature/lounge-major-events-home-20261103` at `d081d77c`: relevant recent product work, not independently identified as the installed customer package.
- `001-live-sports-reliability` at `ee1aa2f7`: unmerged sports QA. Its new matching implementation is deliberately not introduced by this port.
- Latest Android screenshots inspected from main: Home, Live TV, Movies, Series, Sport Lounge and TV Guide, all 1920×1080.
- The actual currently installed physical Android package could not be inspected here. Published release is the strongest available baseline. Screenshot date is newer than release, so complete build-to-screenshot identity remains an ambiguity.
- Earlier `feature/lg-samsung-lounge-parity-20261028` was inspected for native video lifecycle only. Its UI and catalogue were not reused.

## Backend evidence

Read-only source inspection of deployed `lounge-core-xtream` **v49** confirms canonical customer categories and external IDs, customer authentication, EPG action, movie/series details and playback routes. No query of customer passwords/accounts was used. A live unauthenticated request to `connect.loungetv.nz/player_api.php` returned 401 with CORS `*`; sports calendar and Lounge metadata Trending endpoints returned real JSON.

The deployed gateway selects mapped playback sources and formats. Asking for `.m3u8` in a client URL does not currently establish that the gateway will return HLS. The LG adapter uses the gateway route and does not guess or expose a provider URL. No working-stream claim is made.

Android Movie/Series ViewModels use local `FavoriteDao`, `ProgressDao`, `ProfileDao`, Room and a backup writer. No shared account-state API was found in the inspected client path. The LG project stores state under customer username and profile on this TV. Shared state is not fabricated, and no production schema has been added.

## Screen matrix

| Area | Android reference | LG implementation | Verification | Remaining discrepancy and next correction |
|---|---|---|---|---|
| Shell | `OwnTVShell.kt`, `StageRail.kt`, `StageTokens.kt` | Dark palette, original Jakarta font and Stage icon paths; fixed solid 128 px icons-only rail; NZ clock; profile/last-channel controls | Browser at 1920×1080; no pixel equality claim | Full navigation preferences, alternate themes and Downloads/recording are not ported. Weather is omitted because its configured Android location is local state, not account data. |
| Home | `HomeScreen.kt`, `MajorEventsHero.kt`, `MajorEventSelection.kt`, `LoungeHomeHighlights.kt` | 520 px sports hero at y110; 10 s carousel; backend schedule; original sport images; reminders; Trending; Keep watching; editorial shelves | Browser mocked contracts; actual calendar/metadata endpoint reachability | Local Trending only reads first movie/TV pages and uses strict title/year matches, rather than Android's multi-page scored matching and persistent snapshot. Hero headline ranking is a smaller translation, not a literal port. Hero fold, preview/trailer and complete Android user row configuration remain incomplete. These are correctable client work, not platform limitations. |
| Live | `LiveScreen.kt`, `LiveStage.kt`, `LiveViewModel.kt`, Lounge category helpers | Real gateway categories/order and IDs; 84 px rows, 92 px pitch; programme pane; selected channel; schedule; favourites; native video | 2,391-row mocked catalogue, bounded DOM, 50-down focus return test | A persistent Live category panel now expands canonical headings into subcategories and retains every backend folder. Now/next reads the cached guide for visible rows; selected-channel fallback is deduplicated. Android's full category customisation remains incomplete. Backend unchanged. Preview starts only after explicit playback. Physical tune/Back test outstanding. |
| Movies | `MoviesScreen.kt`, `MovieViewModel.kt`, `VodStage.kt` | Cinematic details/background, 6-column virtual grid, metadata, category/search/sort, Saved and resume | 5,142-row mock; bounded DOM, grid cursor restore, hold OK Saved | All detail-panel components, cast artwork, metadata refresh controls and sort choices are not yet identical. Exact pixel/crop acceptance incomplete. |
| Series | `SeriesScreen.kt`, `SeriesViewModel.kt` | Browse, season-keyed and flat episode contracts, details, seasons/episodes, progress, next episode | 3,395-series mock and correct authenticated episode playback URL | Cast/artwork/detail parity has same gap as Movies. Actual next-episode playback must be tested on LG. |
| Sport Lounge | SportLoungeDiscovery and existing calendar services | Same 31 primary feed definitions, NZ seven-day window, nested fixture parsing, progressive loading, supplemental calendar merge and existing backend candidates | Parser/discovery tests; mocked primary+supplemental browser journey | Discovery population is ported; full match-centre/standings/team UI parity and exact broadcast verification remain incomplete. No sports matcher is changed. |
| Saved | `SavedScreen.kt`, `MoreCountsViewModel.kt` | Movies, Series, Sport, Kids, Entertainment groups and reminders; hold OK remove | Account/profile isolation and browser Saved tests | Classification currently follows a smaller name-based equivalent. Android's full category/tag classification should be ported. Local state cannot mirror another device until a shared state contract exists. |
| Search | `SearchScreen.kt`, search ViewModel | Current catalogue across live, movie and series; remote input, type filters and routes | Browser search across catalogue types | Programme/EPG search and Android spotlight/result composition are not fully ported. Correctable. |
| Guide | `GuideStage.kt` and Android EPG reader | Selected category, now/earlier/later, real short-guide entries, time-proportional cells and channel paging | Mocked epoch/base64 guide and focus isolation | Now uses a device-local full XMLTV cache, with short-guide fallback. Explicit date picking and smooth two-axis virtual timeline remain incomplete. Correctable without changing the backend. |
| Settings | Android v5.2.3.10 SettingsScreen, SettingsGroup and VideoPlayerSettingsScreen | Twelve groups; 110 imported row definitions; Search/help/Quick; working playlist, EPG, profile, backup and exposed preferences; unavailable labels for unported controls | Browser group/search/preference/source tests and screenshots | Full functional parity remains incomplete, including kids/PIN controls, advanced themes/layout, recording, external players, device DNS/proxy and Android engine options. An unavailable row is not a completed feature. |
| Playback | Android playback flow; native LG HTMLVideoElement adapter | One native surface, foreground live-pause/EOF/stall recovery, bounded retries per outage, decode/frame diagnostics, native fullscreen geometry and Left/Back drawers | User: 15 minutes Live on 0.1.2 without the former stop. Native lifecycle tests and browser regressions pass | New channel-start latency, codec/track coverage and sustained VOD/episode playback require LG testing. ExoPlayer/MPV have not been ported. |
| Profiles/state | Android Room + backup writer | Local per-customer/profile state, same relevant user behaviour where implemented | Domain and browser isolation tests | Cross-device sync not available in inspected API. QA login persistence uses app-local storage, not Android Keystore encryption. Production credential-storage decision remains outstanding. |

## Settings classification

| Classification | Treatment |
|---|---|
| Product | Twelve Settings groups and 110 source row definitions are present. Supported controls are wired; unavailable features remain explicit work. Profiles, source/guide management, local backup and common playback preferences are implemented. |
| Android specific | Android installer, launcher channels, Android external players, filesystem recording/download paths excluded. |
| Engine specific | MPV/Exo toggles replaced with one native LG video adapter; native-exposed tracks and aspect controls implemented. |
| Developer/internal | Redacted local playback trace available in this QA build. No provider/admin credentials, source URLs or database controls. |

## Acceptance status

**QA 0.1.3 remains an isolated QA build, not a finished exact replica.** User testing confirmed improved responsiveness in 0.1.1 and 15 minutes of Live continuity in 0.1.2. The new VOD/guide/Settings/sports changes still need physical LG acceptance. Screen/row presence and passing mock checks do not establish functional parity. The workspace cannot reach the TV LAN address.
