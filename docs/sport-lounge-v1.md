# Sport Lounge V1: Codex implementation brief

Status: implementation specification (not a completed feature).
Base: `feature/sport-lounge-v1-20261026` branched from `recovery/known-good-5.2.2.13-20261026` / release `v5.2.2.13` (`bc8ad1562f4e3262338972f9d5768cfd02ad5dd2`).
Owner application: `Gazzamoo111/lounge-tv-player`.

## Objective

Build **Sport Lounge**, a TV-first, event-led sports home inside Lounge TV. The user must immediately see what's live, what starts today, what is coming up, and which available BEST channels might carry an event. Emphasise NZ/AU relevance, especially NZ Monday NFL, rugby league, rugby union, international cricket, NBA/Australian NBL, netball, major football, motorsport and UFC/boxing. It must be useful to a new profile with zero followed leagues.

Do not confuse **sports fixture availability** (schedule/score data) with **stream availability** (BEST channel + guide mapping) or **verified playback** (a stream actually plays). Never invent event data or claim a stream works merely because a matching channel name was found.

## Protected production behaviours

- Preserve `com.loungetv.player`, production signing, update compatibility and customer data; don't publish or install into production.
- Keep the Lounge TV Option C identity, static expanded sidebar, `My Lounge` name and star icon, Home, Live TV, Guide/automatic dual EPG, Movies, Series, BEST integration and existing player behaviour unchanged.
- Preserve NZ Sky Sport RAW/HD and the recovered working player. Do not cherry-pick newer `main` changes blindly; inspect conflicts and test separately.
- Only BEST provides video streams. Sports data may come from independent fixture providers.
- Do not introduce an external paid API, deploy infrastructure, migrate databases, or modify account settings without a clear explanation of the specific credentials/costs and an explicit go-ahead.
- OwnTV core sports data / notification logic is in the separate dependency `ahXN00/OwnTV_Core`, resolved as a published library per `settings.gradle.kts`. Avoid editing/publishing upstream or assuming app-local source for core. Any adapter should live in the fork or a separately scoped service, with tests.

## Current code to inspect FIRST

- `app/src/main/java/tv/own/owntv/features/sports/SportsScreen.kt`
- `app/src/main/java/tv/own/owntv/features/sports/SportsHubScreen.kt`
- `app/src/main/java/tv/own/owntv/features/sports/SportsViewModel.kt`
- `app/src/main/java/tv/own/owntv/features/sports/SportsMatchScreen.kt`
- `app/src/main/java/tv/own/owntv/features/sports/SportsParts.kt`
- `app/src/main/java/tv/own/owntv/features/shell/OwnTVShell.kt`
- `app/src/main/java/tv/own/owntv/features/home/`
- `settings.gradle.kts`, `app/build.gradle.kts`, `.github/workflows/ci.yml` and `publish.yml`.
- Upstream core: `core/src/main/java/tv/own/owntv/core/sports/{SportsCatalog,SportsRepository,SportsSource,SportsAlertsSetup,MatchAlertScheduler}.kt` in `ahXN00/OwnTV_Core`.

Important facts: current hub draws horizontal `Live Now` / `Sport on your channels now` / `Starting Soon` sections, has an oversized first featured card, defaults to followed leagues, and truncates `soon` to 12. Current core sports window is approx. 36 hours ahead; do not present that as a full weekly calendar. The existing `SportsAlertsSetup.remind` is channel-dependent for TV; core background notifications are phone-focused. QA CI without persistent signing credentials fails on workflow_dispatch/main; PR preflight builds are separate ephemeral `.eventqa` identities.

## Delivery sequence

Implement in **small, testable slices**. Continue after each passing test gate. Prefer a working limited-coverage feature over a polished mock.

### Slice A: Functional event-led Sport Lounge hub (first code milestone)

1. Replace the body of the existing Sports hub with an event-led `SPORT LOUNGE` experience while retaining `SportsScreen` navigation, `SportsMatchScreen`, current playback and settings.
2. Create pure view-state/filter/ordering helpers, independently unit-tested. Use existing real `SportsBoard` matches as first provider. Do not add hardcoded example matches to runtime.
3. Filters: **Live Now**, **Today**, **Upcoming** with true counts from the displayed dataset; horizontal sports chips: **All Sports**, NFL, League, Rugby, Cricket, Basketball, Football, Combat, Motorsport and additional categories supported by actual data. Add `My Teams` and optional followed-league emphasis but do not require setup to see useful matches.
4. Three equal-size, readable event cards per 1080p row where real available content width allows. Use an adaptive fallback for smaller widths, not an absolute hardcoded 3. Every card shows sport/league, true status/kickoff in device-local timezone (NZ user context, support DST), names/crests if available, score only if score-spoiler preference permits, and reminder state only when a reminder is truly set. Clear disabled/unavailable channel indication. No oversized first card, advertisements, crowded pills or clipping. Prefer the existing Lounge TV theme tokens and subtle blue focus cues.
5. Event status updates while displayed from existing streams, no `System.currentTimeMillis()` as frozen `remember` UI for a long-running Live tab. Focus restoration on Back from Match Centre, D-pad up/down/left/right, tab switching, scroll end and long-OK must work. Preserve main sidebar entry and Back contracts.
6. Empty and stale-data states must state the truth, for example `No live fixtures from connected sports sources`, `Schedule last updated ...`, not `Nothing on today` if the source has incomplete coverage.
7. Keep existing `Find channels` action in Match Centre; never display `Watch Now` without a supported match to BEST channel mapping. Keep sports source names out of branded UI if inconsistent with brand, but retain diagnostic provenance in logs.

**Slice A gate**: compile; unit tests for status/filter/dedup/sort/local NZ time; no fake matches, no broken route, no screen clipping or stuck focus; annotate known coverage gaps. If zero-followed profiles lack default events because core only loads followed leagues, don't hide the issue: implement a bounded **opt-in-independent discovery source** or move immediately to Slice B before claiming success.

### Slice B: Broad sports fixture coverage, a reliable central gateway

1. Add a clean provider-neutral `SportEvent` model and adapter contract: stable external event ID + source, sport, league, competitors, start UTC epoch, state `scheduled/live/finished/postponed/cancelled`, score if present, source timestamp, last successful update, optional league/crest info. Use stable IDs and sensible dedupe with explicit precedence, not fuzzy merging of unrelated matches.
2. Audit real fixture availability and freshness for priority sports: Rugby League World Cup (including women's if available), NFL, rugby internationals, cricket, Australian NBL / NZ Breakers, NZ/international netball; then NBA, major football, F1 and UFC. Store a per-competition supported/unsupported report in `docs/sport-lounge-coverage.md`. Do not invent missing fixtures.
3. Prefer a Cloudflare Worker central read-only fixtures endpoint and small caching layer to avoid thousands of devices polling third-party APIs individually. Define `/v1/sports/events?from=...&to=...&sport=...` response contract and fixtures/status diagnostics. Implement a minimal deployable service under `services/sport-lounge/` only when a verified permitted feed is available. No secrets committed; no live deployment without permission.
4. Evaluate providers (existing ESPN source, TheSportsDB, others) against actual priority coverage and rate limits. Identify any pricing/keys that require decision. Explicitly avoid scraping unsupported sources or pretending unreliable ESPN endpoints offer complete world coverage. Retain safe stale cache with `lastUpdated` on provider failure, no fake live status.
5. Support NZ local-day slicing (American Sunday games appear NZ Monday), timezone and DST correctness, duplicate prevention, provider 429/403 backoff, out-of-order status updates, degraded/offline network and empty coverage.
6. The app consumes central events through a separate adapter and merges with existing `SportsBoard` only where reliably matched; keep existing `Match` pages for those with a supported mapping. For additional sport types not represented by the upstream `Sport` enum (e.g. netball), show a clean fixture detail view rather than crashing or fabricating a `Match`.

**Slice B gate**: fixture accuracy audit covers each priority sport, transparent `unsupported` notes, testable gateway with mock fixture providers, cache handling, stable IDs, and zero fake live events. Do not claim universal coverage unless verified.

### Slice C: Reminders, follow, Watch options

1. Introduce **fixture-based Remind Me** independent of BEST EPG/channel discovery. Show `Remind Me`, `Reminder Set` and `Remove Reminder` states, per profile. Handle event postponement/start-time change, duplicate suppression and app restart. Support an in-app reminder when Lounge TV is running; do not promise notifications when a Fire TV is powered off.
2. Reuse existing settings/followed teams where possible. Keep favourites clearly separate from `My Lounge` video favourites. No unsolicited broad push alerts.
3. Cross-device, phone/email/WhatsApp alerts are a separately opt-in later deliverable; do not quietly add personal-data capture or expensive notification infrastructure.
4. Event-to-channel matching is a distinct stage: find channels only from the configured BEST playlist and EPG, prefer NZ Sky Sport RAW where appropriate if actually mapped, respect raw/HD availability and language settings. A candidate is not proof of playback. No numbered PPV feed advertised as verified unless explicitly mapped and tested.
5. Continue using the existing player. No sudden live channel switching or forced player interruption. No auto-multiview in V1.

**Slice C gate**: setting/removing fixture reminder works with no channel; survives app restart; no duplicate alerts; schedule change handled; watch links never claim verified playback based only on naming.

## Tests and quality guardrails

- Pure tests: dedupe, score/status chronology, 2026-10-26 NZ Monday NFL local-day conversion, DST transitions, sorting Live > NZ/AU priority > soon, status filters, empty/error/stale coverage, reminder reschedule/cancel, no false watchability.
- UI/device: Fire TV D-pad/focus, scroll, stable keys, background/foreground, 720p/1080p, text overflow, bottom row/overscan, Back to exact card, long OK menu, screen reader labels where existing patterns support them.
- Check no regression to Home/Live/Guide/NZ Sky RAW/HD, existing EPG integration, Movies/Series, customer activation and installed data.
- Run `./gradlew :app:testStandardDebugUnitTest :app:assembleStandardDebug` after changes. CI QA stable credentials may be unavailable: use isolated eventqa/debug preflight without uninstalling or overwriting existing production or QA. Do not weaken signature checks, publish a release, or change production package.
- Create commits and a draft PR from this feature branch; in final implementation summary report actual commits, tests, QA APK path/artifact, real sport coverage count by provider and known missing competitions. Never mark a test passed unless executed.

## Change boundaries and decisions

Deliver Slice A first and continue toward B/C only after A works. If paid fixture provider/API key is required, implement the adapter and tests, surface the cost/coverage blocker and stop that integration pending approval; continue all unblocked work. No mock events shown as genuine fixtures in production. Do not modify Core or our public website/admin/Cloudflare/Supabase production settings merely to make the demo look complete.
