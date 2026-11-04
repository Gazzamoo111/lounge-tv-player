# Implementation Plan: Lounge TV Live Sports Reliability Overhaul

**Status**: Draft proposal for GPT Work review; documentation only  
**Branch**: Existing feature/lounge-major-events-home-20261103, d081d77cbec6f5f498a16c58250d96f440842c90  
**Date**: 2026-11-05 Pacific/Auckland  
**Spec**: [spec.md](spec.md)  
**Owner**: GPT Work for implementation; Codex for independent verification  
**Authorization**: Gary requested a precise specification, plan and test matrix. No implementation/build/deployment/APK replacement authorized. Publication and branch changes require separate authorization.

## Summary
Replace loose network confidence with fixture evidence evaluated consistently by backend and client. Curate Soccer through existing Sports folder semantics, fix only evidenced guide mappings, enforce source-scoped AU ESPN1 exclusion, instrument existing player switching, and make Major Events timely and truthful. Preserve existing sports/VOD behavior and production settings.

## Technical Context
- Android Kotlin/Compose application, minimum SDK26, existing player-core1.0.69 and mpv/Exo paths. Preserve dependency pins; inspect resolved player-core and applied `scripts/patch_lounge_playback_core.py` before changes.
- Supabase Deno/TypeScript candidates function uses postgres3.4.7 and current BEST inventory/category tables. Deployed v9 differs from checked-in source. Gateway is Node `.mjs` under services/sport-lounge.
- Storage: existing BEST inventory/import/category/selector records, EPG state/programmes and Android saved preferences. No new persistence or migration assumed. Proposed decision/evidence types may remain transient.
- Testing: Kotlin JUnit, Node test runner, Python guardrail tests, credential-free backend pure matcher harness to be added, then explicitly authorized sequential device QA. No current test pass claimed.
- Constraints: no provider bursts, no credential leakage, no replacement application branch, no production mutations/config changes; retain source identifiers and fallback behavior.
- Goals: exact matrix pass, six initial recommendations, twelve per alternatives page, visible transition <=100ms, hero expiry<=60s, non-regressing paired switch overhead and connection counts. Stream latency is measured separately from application delay.

## Constitution Check

| Principle | Design gate before implementation and again after design | Current result |
| --- | --- | --- |
| I Production Safety | Read-only production evidence, isolated QA backend and provider account, exact change authorization | Planning passes; implementation/release gate closed |
| II QA First | Baseline + matrix before code; distinct package/key AND backend isolation | Baseline/matrix drafted; execution pending |
| III Playback Reliability | Live, reconnect, VOD resume/series/audio/subtitles/lifecycle preservation | Mandatory matrix rows, unexecuted |
| IV Regression Prevention | Original defect fails, revised behavior passes on exact source/patch | Cases specified; not run |
| V Catalogue Integrity | Source-scoped deterministic transforms and stable references | Contract defined; content mapping still requires evidence |
| VI Sport Accuracy | Identity, timezone, provenance, freshness and honest uncertainty | Contract defined; clock/alias boundary cases required |
| VII Artwork Quality | Preserve artwork and inspect TV crop/focus/fallback | Device/visual gate pending |
| VIII Security | No secrets; review input/auth/rate limits; no unsolicited access-policy changes | No secret export; production access review remains separate |
| IX Controlled Releases | Immutable commit/artifact/signing/version, exact approval, recovery | No release authorized |
| X Minimal Reversible | Narrow feature diff; original branches/untracked work preserved | Documentation-only delivery; source untouched |

Post-design check: no planned exception. Unknown stream identity is explicitly blocked from automatic remap and verification, rather than bypassing a gate. Freshness/coverage gaps are implementation prerequisites and must not be marked resolved by this plan.

## Project Structure
Feature records belong in `specs/001-live-sports-reliability/`: spec, plan, research, data-model, contracts, acceptance-matrix, quickstart, tasks and verification. Delivered local outputs preserve this hierarchy; not pushed to GitHub.

Affected source areas:
- `backend/supabase/functions/lounge-sports-candidates/`: deterministic evidence matcher, bounded API and QA tests; production config unchanged.
- `services/sport-lounge/src/index.mjs` and tests: gateway contract pass-through, compatibility, timeout/cache scope.
- Android `features/sports/BackendSportsCandidates.kt`, `SportsViewModel.kt`, Sport Lounge resolution/UI/data models: tier validation, merge and alternative discovery.
- `features/live/LoungeLiveSections.kt`, `LoungeLiveCatalogueCleanup.kt`, `LoungeLiveDuplicates.kt`, catalogue/search/tune entry points: curated Soccer and common exclusion policy.
- Existing EPG mapping/ingestion/resolver path: trace ownership before editing, repair supported service mappings only. Mapping ownership may be upstream rather than app code; no invented file or table write is prescribed.
- `features/live/LiveViewModel.kt`, `ZapTuneDelay.kt`, player `MpvVideoSurface.kt`, existing ZapTiming and resolved player-core: instrumentation and generation-safe display.
- `features/home/MajorEventSelection.kt`, `MajorEventsHero.kt`, `features/sports/SportsViewModel.kt`: clock-driven selection and lifecycle.

## Phase 0: Research and baseline
1. Reconfirm GitHub/local refs and unpushed work with GPT Work; retain adoption branch. Capture deployed/source function diff, exact application/core patch identity and existing CI checks without running release workflows.
2. Freeze sanitized catalogue/guide/fixture snapshots and successful-import metadata. Re-read stalled import health and identify owner; report stale data before relying on it. No production repair under this task.
3. Inventory every current sports/VOD feature and every visibility/tune entry point. Capture baseline provider opens, stale-frame video and timing only when separately authorized in isolated QA.
4. Resolve mapping ownership and service identities for beIN/Fox/Kayo. Record name/guide disagreement, missing state and empty programme sets separately. Do not guess mappings from numbers.

## Phase 1: Design and contracts
Use [candidate/UI contract](contracts/candidate-and-ui.md) and [data model](data-model.md). GPT Work reviews feasibility; any proposed cap/freshness/identity change is recorded in the documents before code. Version contract while keeping legacy requests usable with downgraded trust. Backend v2 must not cause older clients to interpret weak results as verified: emit legacy confidence fields only for evidence that satisfies the old promoted meaning, or negotiate a v2-only path and leave legacy response semantics explicitly bounded. Test old-client/new-backend and new-client/old-backend matrices in isolation. No live rollout order is approved.

## Phase 2: Implementation sequence for later authorization
1. Add pure matcher and client regression fixtures R01-R10 before behavior changes. Preserve originals as failing baselines, including deployed v9 separately from repository regex fixes.
2. Implement common service identity/exclusion policy and typed fixture/time evidence. Add input bounds, deterministic ranking/caps/paging and stable versioned cache scope. Keep credentials/playback resolution on existing paths.
3. Update client parser and merge together: distrust legacy confidence; preserve strongest local or backend evidence per service; separately retain variants. Keep loading/error/fallback/manual choice and remembered-channel behavior. Add honest tier labels/explanations.
4. Derive Soccer dynamically using existing section semantics and reviewed service rules; test synthetic large inactive pools and actual catalogue snapshots. Do not unhide all PPV categories. Apply AU ESPN1 suppression to every display and tune entry point while preserving ESPN2 and audit rows.
5. Prepare a mapping-change manifest with old/new source-scoped IDs, evidence, affected counts and recovery for beIN/Fox/Kayo. Implement only reviewed mappings in isolated fixtures/QA data. For genuine guide-source coverage outages report upstream gap rather than fabricate a guide. Record actual programme comparison before any remap reaches review.
6. Extend existing ZapTiming with generation-bound player/UI milestones, then diagnose the measured slow segment. Make only evidence-supported preparation/buffering/transition changes. Keep current debounce and provider-open limits unless a separately reviewed experiment proves equivalent connection safety. Do not change VOD load control globally.
7. Implement strict live partition, refresh/expiry clock, stable selected key and scoped variety while preserving editorial/artwork/reminder/navigation behavior. Keep safe View event default.
8. GPT Work supplies narrow diff, complete task evidence and exact source identity. Codex independently verifies matrix, source and artifacts; gaps remain unverified. Build/device/release steps require task-specific authorization and isolation.

## QA isolation, audit protocol and recovery
Before any runtime test: verify non-production application ID/signing identity, isolated endpoint/database writes, test provider account/session limit and redacted logs. Package isolation alone is insufficient. Mock fixture/catalogue/EPG inputs first. Do not invoke deployed production function as a test if its side effects are unknown; deployed source inspection suffices for this specification.

Later approved playback audit covers each beIN AU1/2/3, all identified UK-labelled variants, Fox501-507 and each listed Kayo backup. Sequentially sample each distinct variant for five minutes under the same recorded network profile; use three tune attempts per variant, closing prior connection before next. Record tune-to-first-frame distribution, stalls/count/duration/rebuffer ratio, resolution/frame rate/bitrate/codec/audio and simultaneous screen+guide evidence. A deterministic moving QA stream targets p95 first frame<=3s, zero incorrect frames and zero rebuffers in five minutes; real provider samples report observed metrics and baseline comparison, with >1% rebuffer ratio, repeated failed tunes or content mismatch flagged unsuitable for promotion. A static slate does not establish motion health. These thresholds classify the sample, not guarantee future service quality. Backoff/stop on rate limiting; no concurrent probing.

No production recovery is needed for this documentation-only task. For a later candidate retain previous app configuration, mapping manifest and backend source/version. Recover through reviewed forward/revert commits and isolated state restoration; never reset original work or delete inventory. Backend/app releases remain independently approved, compatible and reversible. No remote flag/config change is authorized as a shortcut.

## Complexity Tracking
No constitution exception. Separate fixture trust, playback health and catalogue visibility because collapsing them causes the reported defects. Reuse existing folder, timing and carousel machinery instead of introducing new services.
