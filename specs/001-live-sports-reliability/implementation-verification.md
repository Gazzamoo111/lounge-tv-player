# Implementation verification, 5 November 2026 NZ

**Verdict: incomplete, not production-ready. Confidence: Certain.** This is the implementer's report. Codex independent verification T022 remains pending. No complete acceptance-matrix pass is claimed.

## Source and restrictions

- Latest remote sports development confirmed: d081d77cbec6f5f498a16c58250d96f440842c90.
- Spec Kit integration base: ff69a7a3147c37b6ab3be93e7e7a78bae95f527d, one documentation/tooling commit above sports.
- Isolated branch: 001-live-sports-reliability. Draft PR: https://github.com/Gazzamoo111/lounge-tv-player/pull/64.
- Current application candidate: 87a03222cebc7fc8ffb00c387766f8e655c98961. Later report-only commits must identify whether application source changed.
- Playback core: 1de78b1e2690c22b50c060e986bce221454bf880, existing exact fail-closed Lounge playback patch applied. Core dependency pins and patches were not changed.
- Production main, installed production APK, deployed functions, customer data and original development/integration branches were not modified. No stream opened, device installed or production API invoked for runtime tests. SQL/source audits were read-only.
- User authorized implementation, tests and isolated QA APK. No merge, deployment or production APK replacement is authorized. Draft PR is a review vehicle, not release permission.

## Implemented changes and practical limits

Evidence matcher: pure fixture/time/competition/division/market decisions, expiry, source-scoped ESPN1 policy, logical service grouping, initial six and alternate-network reservation. HTTP request bounds and v2 envelope/paging are present; legacy results use maybe only. Frozen source replays reproduce original repository and deployed-v9 name-only confidence, UK-market and football defects.

**The database adapter still lacks a reviewed regional service/programme evidence producer.** It explicitly returns unverified discovery, rather than manufacture evidence from names. Pure matcher success does not establish end-to-end verified matching. The Android parser validates v2 envelope and evidence conservatively, downgrades legacy authority and retains local alternatives. Its current core WatchOption model does not carry all local programme/freshness/fixture-end metadata. Initial six/12-item alternatives are present, but backend nextCursor is not yet wired to client loading. These are implementation gaps, not device-only checks.

Soccer: a reversible virtual Sports folder uses current Room inventory, scoped BEST identities and exact reviewed AU beIN aliases. A pure event-evidence admission seam rejects the 300-feed noise fixture. **Runtime temporary-feed evidence is not wired**, and the continuous-service review currently covers only the three AU beIN services. No PPV folder globally unhidden, no saved references rewritten.

ESPN1: source-scoped shared identity/alias policy applied to Live list/search/count predicates, preview, internal/external/tiles/tune/by-ID/catch-up, Home recent/favourite/Continue, and Sport Lounge merge/backend. Saved records and inventory retained. Complete startup/deep-link/scheduled recording/multiple-source/renamed canonical-ID coverage is not established.

Guide and buffering: ownership and current per-service coverage audited in guide-audit.md and the frozen catalogue snapshot. All targets have zero programmes in the now-to-24-hour database window. Missing state and explicit empty states are retained separately. No guide repair is supported without actual content evidence, so no mapping changed. No buffering improvement or stream-health claim is made.

Switching: app-level bounded generation trace and tests, cancellation guard on delayed zaps, controller-resolution milestones, and explicit previous-picture label for the existing mpv-to-Exo freeze overlay. **Full engine callback/first-frame/overlay ownership and diagnostic export are not wired to the new trace.** Existing core generation gates and debounce remain unchanged. No latency, connection-count or stale-frame visual pass is claimed.

Hero: strict fresh-live partition, exclusive seven-day upper bound, 30-second expiry ticks, source observation timestamps, bounded visible live-feed refresh with at most three simultaneous fixture fetches and 15-second total timeout, stable selected key and next surviving key, pause while focused, preserved artwork/reminders and safe event navigation. Missing live provenance fails closed. Explicit end-time propagation and complete resume/action revalidation need further coverage.

## Automated evidence

- Node gateway/backend suites: 37 tests passed locally, including HTTP adapter mocks and actual immutable original-source replays. No provider requests in tests.
- Python guardrails: 27 tests passed locally against the unchanged pinned core patcher.
- Literal inventory and number-locale checks passed; user-facing labels extracted to Android string resources. Existing baseline debt shrank by three stale entries. Technical protocol/log literals were explicitly classified; no UI copy classified technical.
- Branding check passed after the existing CI asset-generation steps. Generated assets are build inputs only, not artwork-source changes in this PR.
- Text-overflow check found eight pre-existing failures in unchanged files. They are not represented as a pass.
- Android tests, APK metadata and CI results: append exact final results below.

## Acceptance matrix status

Partial means a subset has automated evidence, with the stated remainder still open. No row is fully accepted until all its required checks pass on the exact artifact.

| Case | Status | Evidence / unresolved work |
|---|---|---|
| R01 | Partial | Pure fixture-vs-network and source replay pass; reviewed database evidence adapter missing |
| R02 | Partial | Pure identity/time/division contradictions pass; full client/real snapshot integration missing |
| R03 | Partial | Service-market assignment tests and original UK defect replay; actual regional identity audit pending |
| R04 | Partial | Inactive and contradiction tests; full AU/UK content window integration pending |
| R05 | Partial | Typed cross-sport contradiction tests; complete scoped alias corpus pending |
| R06 | Partial | Merge/fallback unit coverage; timeout/503/malformed/version/end-to-end local evidence matrix incomplete |
| R07 | Partial | Original NFL misclassification reproduced and source branch corrected; full taxonomy corpus pending |
| R08 | Partial | HTTP legacy downgrade and client helper tests; full old/new runtime matrix pending |
| R09 | Incomplete | Backend cap/dedupe tests; client cursor loading/complete alternatives/variant UI incomplete |
| R10 | Partial | Strict overlap and 15-minute+1ms boundaries pass; inferred-end/source policy corpus incomplete |
| R11 | Incomplete | Pure freshness/stale-catalogue checks pass; ongoing client candidate expiry/refresh incomplete |
| R12 | Partial | Source-market assignment logic and separator baseline retained; complete separators/Seven corpus pending |
| S01 | Partial | Pure 300-feed-noise test; runtime event-evidence producer and full continuous list incomplete |
| S02 | Unverified | No deletion/ID rewrite; complete refresh/rename/remove/saved navigation fixture absent |
| S03 | Partial | Sports virtual folder implemented without global unhide; TV navigation and actual eligible events pending |
| E01 | Partial | Exact ID/alias policy plus ESPN2/10/11/US positive controls; full source-path coverage pending |
| E02 | Incomplete | Multiple display/tune guards; complete startup/deep-link/scheduled recording tests not established |
| G01 | Partial | All six catalogue/state observations saved; Android visibility and actual playback unverified |
| G02 | Unverified | Seven differing .qa mappings retained; no observed-content audit or remap |
| G03 | Partial | Missing and empty distinguished in snapshot; one-hour shift/content mismatch replay incomplete |
| F01 | Partial | Every Fox/Kayo target audited with zero database 24-hour programme coverage; no fabricated guide |
| F02 | Unverified | No device/provider sampling, first-frame/stalls/quality/audio measurements |
| P01 | Unverified | No 30 paired isolated samples per engine/device; <=100ms and p95 not proved |
| P02 | Partial | Existing delay constants preserved; full fake-clock cancellation/open-count and 10 bursts pending |
| P03 | Incomplete | Generation helper tests and previous-picture label; actual frame/engine callback binding missing |
| P04 | Unverified | No target-device HLS/TS/DRM/reconnect/VOD/resume/seek/audio/subtitle/AFR run |
| H01 | Partial | Strict live partition unit test; target-TV presentation pending |
| H02 | Partial | Exclusive 168h boundary, max12, existing variety tests; full rotation cycle test pending |
| H03 | Partial | Timestamp expiry/tick/visible refresh implemented; lifecycle/error/explicit-end corpus incomplete |
| H04 | Partial | Key reorder/removal unit tests; focused TV interactions and expiry-click test pending |
| H05 | Partial | Safe event action retained, no new Watch Live; complete badge/availability screenshot evidence pending |
| T01 | Partial | Existing NZ DST test retained; exact NZ autumn/UK boundaries and reminder rescheduling corpus incomplete |
| V01 | Unverified | Existing automated suites retained; exact target-device feature-preservation pass pending |
| V02 | Unverified | No customer-data mutation or production deployment; target-device VOD/profile/saved-flow pass pending |

## Release blockers

Finish evidence production, candidate expiry/pagination, local evidence normalization, runtime Soccer admission and complete exclusion paths. Complete engine milestone/frame ownership before any switching-performance claim. Restore upstream guide coverage through an isolated reviewed mapping/ingestion candidate after content observation. Verify separate backend/database/provider isolation, then sequentially run G/F/P/V device protocols. Codex independently verifies the final immutable source/artifact. Production gate remains closed.

## Local Android build evidence

Built successfully with JDK21, Gradle9.7.1, pinned patched core and existing generated-asset pipeline:
`RELIABILITY_AUDIT_QA=true ISOLATED_EVENT_QA=true gradle :app:testStandardDebugUnitTest :app:assembleStandardDebug -Powntv.corePath=<pinned-core>`.

App tests: **280 passed, 0 failures/errors/skips, 53 suites**. Per-suite counts are in android-test-summary.json. Local build output: com.loungetv.player.auditqa, versionCode5020301, versionName5.2.3.1-local-qa, minSDK26. APK size105375446 bytes. SHA25625f5142d668c0bd4f28a9be0d1169627170607c2bf859982dd70985c0643f980. Android Debug certificate SHA25616512046c2d898b372d552884862b357313697ef9a9617e31ebda9d38624afad. Signature verified using apksigner. This APK has not been installed or exercised against any backend/provider.

Core patcher SHA256a0f99978ce2bd83ce413894ecf7c01226be9c510241d32727f74f010aad60565; playback-reliability.patch SHA2565fe79f211e82f282585ac3352bf98c01861e35e84c086b3917fa11d0cc4d8edc. No changes to either.

Local build is compiled QA, not accepted runtime QA. Separate database/backend/provider isolation remains unproved, so do not use this artifact to imply successful production service testing. CI #503 failed because the original-source test assumed Git history; the source is now frozen as fixtures and #504 runs the corrected candidate.
