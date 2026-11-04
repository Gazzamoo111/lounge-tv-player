# Tasks: Lounge TV Live Sports Reliability Overhaul

**Status**: Future work, not authorization to implement. All tasks unchecked.  
**Input**: spec.md, plan.md, research.md, data-model.md, contracts/, acceptance-matrix.md.  
**Assignee default**: GPT Work. Codex owns T022 independent verification. Gary owns scope/build/release authorization. Repository paths below are relative to the agreed future implementation checkout; new files are explicitly described as proposed. Evidence goes in this feature's verification.md. Dependencies are stated by phase and task IDs.

## Phase 1: Setup
- [ ] T001 Reconfirm local/GitHub/deployed baselines and unpushed work; update specs/001-live-sports-reliability/research.md and verification.md with exact refs, source/deployed diff and catalogue import health. Completion: immutable identities recorded; no source mutation. Depends: no prior task.
- [ ] T002 Review scope/contracts with GPT Work and Gary in specs/001-live-sports-reliability/plan.md; record feasibility, acceptance and separate implementation/build restrictions. Completion: explicit authorized next scope, no inferred release authority. Depends:T001.

## Phase 2: Foundational prerequisites
- [ ] T003 Create sanitized frozen fixture/catalogue/EPG evidence inputs in proposed backend/supabase/functions/lounge-sports-candidates/test/fixtures/; record hashes and exclusion IDs in specs/001-live-sports-reliability/verification.md. Include current successful import metadata and synthetic clock cases. Completion: no URLs/credentials, repeatable snapshot replay. Depends:T002 and implementation authorization.
- [ ] T004 Define shared identity/evidence/exclusion fields in proposed backend/supabase/functions/lounge-sports-candidates/matcher.mjs and Android features/sports data models. Enforce “Missing end explicitly nullable; inferred duration policy recorded”, “Regional editorial identity evidenced, not inferred from prefix alone”, and “superseded generations cannot complete current state” where applicable. Preserve existing IDs; inputs bounded per contracts/candidate-and-ui.md. Completion: schema/contract cases fail safely; do not implement scoring yet. Depends:T003.

## Phase 3: US1 Correct fixture candidates (P1)
Independent completion: R01-R12 pass through backend, gateway and client while unavailable-backend fallback works.
- [ ] T005 [US1] Add proposed backend/supabase/functions/lounge-sports-candidates/test/matcher.test.mjs cases R01-R05,R07,R09-R12; replay deployed v9 and checked-in baseline separately. Completion: original false-confidence, UK and football defects fail before fixes. Depends:T004.
- [ ] T006 [US1] Replace authoritative confidence assertions in app/src/test/java/tv/own/owntv/features/sports/BackendSportsCandidatesTest.kt with R06/R08/R09, retaining safe fallback. Completion: old client fails unsupported confidence and local-evidence preservation cases. Depends:T005.
- [ ] T007 [US1] Implement deterministic fixture/time/region/competition decisions, common exclusion, service variants, tier caps/paging and expiry in backend/supabase/functions/lounge-sports-candidates/index.ts and proposed matcher.mjs; preserve legacy-safe negotiation. Completion:R01-R12 and input/error cases pass without production calls. Depends:T006.
- [ ] T008 [US1] Update services/sport-lounge/src/index.mjs and test/gateway.test.mjs to forward v2 bounded inputs/results with event/version/revision cache scope and safe timeout/legacy behavior. Completion: old/new compatibility and error paths pass. Depends:T007.
- [ ] T009 [US1] Update app/src/main/java/tv/own/owntv/features/sports/BackendSportsCandidates.kt, SportsViewModel.kt and existing candidate UI to validate evidence and merge strongest per service; show three honest tiers and bounded alternatives. Completion:R06/R08/R09/R11 pass; manual selection/reminders/remembered channel retained. Depends:T008.

## Phase 4: US2 Curated Soccer and AU ESPN1 exclusion (P1)
Independent completion: S01-S03,E01-E02 pass; existing category hiding/favourites/history remain intact.
- [ ] T010 [US2] Add Soccer/exclusion regression cases in app/src/test/java/tv/own/owntv/features/live/LoungeLiveSectionsTest.kt, LoungeLiveCatalogueCleanupTest.kt and LoungeLiveDuplicatesTest.kt; add corresponding matcher tests for fresh/cached backend candidates. Completion:300-feed noise and every ESPN1 entry point covered, ESPN2 positive control. Depends:T004; coordinate shared matcher test ownership after T005.
- [ ] T011 [US2] Implement source-scoped curated service/event admission and stable variants through existing catalogue projection, LoungeLiveSections.kt, LoungeLiveCatalogueCleanup.kt and LoungeLiveDuplicates.kt. Use Sports · Soccer semantics; no wholesale PPV unhide. Completion:S01-S03 pass on synthetic and frozen catalogue deltas. Depends:T010.
- [ ] T012 [US2] Apply common AU ESPN1 exclusion before customer lists/count/search/candidate merge and every tune/saved/last-channel/hero route; document actual enumerated paths in specs/001-live-sports-reliability/verification.md before editing them. Completion:E01-E02 pass; no provider open, no inventory/saved-record deletion, ESPN2 retained. Depends:T011,T009.

## Phase 5: US3 Guide and backup audits (P1)
Independent completion: G01-G03,F01-F02 receive per-service results; an unverified playback row cannot be marked complete.
- [ ] T013 [US3] Trace EPG mapping ingestion/ownership and prepare source-scoped old/new mapping manifest in specs/001-live-sports-reliability/research.md for each beIN/Fox/Kayo target. Completion: missing state versus empty versus content mismatch distinguished; no guessed mapping. Depends:T003.
- [ ] T014 [US3] After separate QA/runtime authorization, capture sequential guide-versus-content and buffering/track evidence for every target in specs/001-live-sports-reliability/verification.md using plan.md protocol. Completion:G01-G03,F01-F02 with actual observed identity; otherwise record blocked/unverified. Depends:T013 and QA isolation.
- [ ] T015 [US3] Apply only evidenced mapping/visibility repairs in the owner files identified by T013, record exact paths and before/after diffs in specs/001-live-sports-reliability/verification.md, and add frozen mapping regression tests. Completion:correct programme/time replay, no cross-region inferred mapping, production unchanged. Depends:T014,T011. Actual mapping owner must be identified before this task can start.

## Phase 6: US4 Channel-switch diagnosis and stale frames (P1)
Independent completion: P01-P04 pass on both target engines/devices with non-increasing provider opens.
- [ ] T016 [US4] Extend cancellation/timing regression coverage in app/src/test/java/tv/own/owntv/features/live/ZapTuneDelayTest.kt and proposed player/TuneGenerationTest.kt for P01-P03. Completion:late A callbacks cannot complete C;1500/500ms boundaries and first+final open limits enforced. Depends:T004.
- [ ] T017 [US4] Extend existing ZapTiming and app/src/main/java/tv/own/owntv/features/live/LiveViewModel.kt plus player/MpvVideoSurface.kt with redacted monotonic milestones and generation-bound overlay ownership. Inspect resolved player-core1.0.69 and scripts/patch_lounge_playback_core.py for missing milestones. Completion:prepare/buffer/UI/debounce segments distinct, stale frames hidden/labelled, no speculative opens. Depends:T016.
- [ ] T018 [US4] Under separately authorized isolated QA, diagnose measured slow segment and propose minimal fix in the identified player/core patch owner; record paired30-isolated/10-burst samples per device/engine in specs/001-live-sports-reliability/verification.md. Completion:P01-P04 and VOD preservation pass, provider opens/retries/concurrency<=baseline. Depends:T017, QA/build/device authorization. Do not alter debounce or VOD defaults without evidence.

## Phase 7: US5 Timely Major Events (P2)
Independent completion: H01-H05,T01 pass including clock ticks without new feed emissions.
- [ ] T019 [US5] Add fixed-clock selection/freshness/rotation tests in app/src/test/java/tv/own/owntv/features/home/MajorEventSelectionTest.kt and HeroEventPresentationTest.kt, plus relevant sports time tests. Completion:original live-order/stale-IN/index behavior fails;168h boundary/DST covered. Depends:T004.
- [ ] T020 [US5] Update app/src/main/java/tv/own/owntv/features/home/MajorEventSelection.kt, MajorEventsHero.kt and features/sports/SportsViewModel.kt for live partitions, stable keys, visible single-flight refresh/clock expiry and honest actions. Completion:H01-H05,T01 pass;safe View event, artwork, reminders and existing ten-second rotation retained. Depends:T019; coordinate SportsViewModel.kt edits after T009.

## Final Phase: Preservation, independent verification and handoff
- [ ] T021 Run relevant existing sports/home/catalogue/gateway/Python guardrail suites and later authorized target-device/VOD checks; record per-row results, source/core patch identity and isolation proof in specs/001-live-sports-reliability/verification.md. Completion:FR001-FR018 and SC001-SC008 have evidence or explicit blockers; no implied pass. Depends:T009,T012,T015,T018,T020.
- [ ] T022 Codex independently reviews exact diff, replays matrix and inspects visual/device evidence in specs/001-live-sports-reliability/verification.md. Completion:issues resolved/retested against same candidate, actual APK hash/signing/version recorded only if build authorized; deployment/APK replacement still requires Gary's exact-candidate approval. Depends:T021.

## Dependencies and parallel opportunities
Setup T001->T002->foundation T003->T004 precedes story work. US1 backend/client is the first useful increment; US2 can begin its own tests after foundation, with integration depending on US1 policy. US3 audit research and US4/US5 tests may run independently after foundation. US3 repair depends on observed content; absent authorization/evidence cannot be bypassed. All stories converge at T021->T022. No implementation work is started by this task list.

Parallel examples after foundation: US1 backend fixture tests while US2 catalogue tests run in separate files; US3 mapping research while US4 generation tests are written; US5 pure selection tests while other stories progress. Shared matcher/BackendSportsCandidates/SportsViewModel files have one writer at a time. No [P] markers are assigned to potentially colliding source edits or provider audits.

## Implementation strategy
Review and authorize first. Prove US1 matching/legacy fallback as the first isolated increment, then curated Soccer/exclusion, evidenced guide repairs and player/hero increments. Each story has independent acceptance above; all seven scope items and preservation gates are required for overhaul completion. Do not release a partial overhaul merely because one increment passes.

Task count22: setup2, foundation2, US1=5, US2=3, US3=3, US4=3, US5=2, final2. No implementation task is checked complete.
