# Implementation authorization and baseline
Gary's 5 November 2026 request supersedes the handoff's documentation-only authorization: implementation, automated tests, isolated QA APK and branch/PR are authorized. Production mutation, main merge and production APK replacement remain prohibited. Independent verification remains Codex's later responsibility.

Base: sports d081d77cbec6f5f498a16c58250d96f440842c90; integration ff69a7a3147c37b6ab3be93e7e7a78bae95f527d (one documentation commit ahead). Fresh clone has no prior untracked work. Isolated review branch 001-live-sports-reliability preserves both existing branches. Production main c24affa6301ff83bbdafaf562d83a499d82c374c. Production function ACTIVE v9 bundle bbd492266a8a615119a4180f1214c3a0e4ba2c0b2b732785ef50b394f3e19f03 re-read without invocation; only fixture separator differs from checked-in implementation.

All 11 handoff documents read, including 34 cases and 22 tasks. Requirements checklist passed with no unchecked items. Prerequisite script passes using SPECIFY_FEATURE_DIRECTORY. No extensions.yml, so no hooks. All seven scopes accepted. Live MPV / VOD Exo and 1500/500ms tune timing preserved. No dependency upgrade or persistence migration planned. Guide fixes require actual content evidence. Provider and device unavailable in this remote workspace; those claims remain blocked.

Gateway inspection: services/sport-lounge is fixture/calendar-only, not a candidate proxy. Candidate endpoint is called directly by Android. T008 requires documenting that ownership rather than adding an unused proxy.

QA: pure fixture tests first. Debug application identity must differ from production. Do not install or exercise production endpoints. Runtime backend/provider isolation must be proved before device testing. Release remains closed.

Hero status provenance is carried separately from pinned core Match, which has no observation/end fields. Unknown live provenance fails closed. Existing discovery polling is not multiplied into 28 seven-day feed requests every minute; visible single-flight refresh still needs a bounded live-status source before that claim passes.

Enumerated ESPN1 surfaces before edits: Live paging/search/custom/favourites/history all pass showInNzSelection; category/all rail counts reuse that predicate; preview, tile, external player, startTune and internal playChannel receive guards; by-ID and previous-channel route through guarded tune. Home recent/favourite and Continue live are separately filtered. Sport Lounge local/backend merge is source-scoped. Remaining direct catch-up/recording and missing-source startup paths require specific runtime coverage. No saved record deleted.
