# QA #667 next development pass

Baseline: `483e020734f6dd0fb0cf1c9eb9ec94fd100d56ce`. Work continues on `feature/lounge-tv-2.0`, draft PR #68. The core remains pinned to `1de78b1e2690c22b50c060e986bce221454bf880`. No PR #70 merge, EPG-source rebuild, global buffer/audio change or multi-provider failover architecture is included.

## Implemented and reviewed

- Compact single-focus Live synopsis with bounded text, programme times/progress, logo/name, next programme and contextual D-pad controls. LEFT/RIGHT browse information; only OK on an explicitly browsed channel tunes; Back closes. Normal hidden-HUD LEFT channel list and RIGHT Recently Watched remain. A transient current-channel/list mismatch cannot silently tune the first old-list channel. The existing playback owner and CH context remain separate from information browsing.
- Distinct coherent vectors for major sections and sports folders, with assignment, path uniqueness, parsing and coordinate-grid checks. Kids uses a kite. Relax parent identity is retained for Nature/Landscapes folders; cricket and basketball artwork received visual corrections. This icon mapping does not admit missing Relax channels.
- Guide/Live ordering snapshots cover Provider/A-Z, editorial tiers, custom folders, favourites/history/catchup and manual movement. Explicit category moves precede editorial defaults; explicit NZ channel moves override launch priority; optional NZ channels retain the selected fallback sort. Guide focuses the playing channel after surfing/return, waits for rows belonging to the requested category/mode, and retains channel identity across background reorder loads.
- Sport Lounge test fixtures use an injected daytime clock with a midnight-boundary regression. QA #677's missing second All Events row was a next-NZ-calendar-day fixture, not an unproved focus-engine defect. Existing D-pad assertions remain.
- QA tennis discovery retains contract-v2 event identity and plausible evidence, but playback now uses the isolated repaired gateway. Other QA sports and all production routes retain their existing endpoints. HTTP integration tests cover candidate routing, encoded credentials, evidence labels, retrospective feeds and missing credentials.

## Automated local evidence

- 1,261 core + 455 player + 370 app JVM tests: **2,086 passed**, zero failures/errors/skips.
- 30 Python/SQLite/patcher tests passed, including scoped manual ordering and exact SQLite A-Z collation.
- 30 Node backend tests passed: ten sports service tests and 20 gateway tests. The latter include 15 executable complete-handler/QA-isolation contracts, reproduce the original NZ routing defect and apply both real patches with zero fuzz.
- Nine existing shared EPG tests passed. App/core literal inventories, generated-branding guardrails and whitespace checks passed.
- X86-64 instrumentation APK compiled locally. Final emulator execution, six before/ownership comparisons, signed ARM APK and signing identity are required CI gates, not inferred from local compilation. Final run/artifact identity is recorded in PR #68 and the delivery report after those gates pass.
- Independent in-session reviews found and corrected missing-anchor OK tuning, NZ A-Z tail parity and stale-category Guide focus. A separate external CLI reviewer was blocked by automatic approval review; no external-review claim is made.

## Isolated gateway evidence

The owner explicitly approved deployment of `lounge-core-xtream-qa-next`. Version 1 is a separate QA function at `https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream-qa-next`, bundle SHA-256 `86bee49e66cd23de544a9ccfd6f160189d5d7545353c125c09dc77b123689378`.

The clone is prepared from the recovered exact v50 source plus the reviewed NZ/tennis patches. It marks responses with `X-Lounge-QA: staged-v50-nz-tennis`, limits callable routes to account/live catalogue/M3U/live/sport, and disables production auth-cache writes. VOD/EPG and other actions are denied. No production endpoint or app production configuration was deployed.

The designated QA account authenticated. Xtream returned a valid 3,013-row live catalogue; M3U returned 3,013 entries, all three clear NZ URLs and two Widevine metadata entries. Actual released external IDs are different from the provider synthetic IDs:

| Channel | Released external ID | MJH source ID | QA route |
|---|---:|---:|---|
| Three | 1486145 | 900000003 | 302 to MJH HLS |
| Whakaata Māori | 1486490 | 900000004 | 302 to MJH HLS |
| Te Reo | 732 | 900000005 | 302 to MJH HLS |
| TVNZ 1 | 1486561 | 900000001 | 422 Widevine client required |
| TVNZ 2 | 1486578 | 900000002 | 422 Widevine client required |

The clear NZ routes do not send customer credentials or synthetic IDs to BEST. Invalid-account access to the clear route returned 403. All three clear feeds exposed H.264/AAC media and passed a three-second host audio/video frame decode. BBC ONE external ID 811 retained the existing BEST mapping to provider ID 392911 and exposed 1080p H.264/AC3 media in the control probe. Short host probes do not prove sustained Fire TV playback, third-party-client DRM support or lip-sync.

PostgreSQL confirms the bounded new tennis term admits all 79 current tennis-named rows in categories 2176/2403. Existing sport predicates remain unchanged; the categories are not broadly whitelisted. Authenticated QA `/sport` returned 302 for named tennis ID 1881735 and placeholder 1881712; unrelated ID 2024978 returned 403. Placeholder admission is route eligibility, not proof of a live event. The historical-event candidate API smoke check returned a valid contract-v2 response with no candidates; no live discovery/playback result is fabricated. Real current-event acceptance remains required.

Production `lounge-core-xtream` remains version 50, bundle `ec3385ec20c0c979214cc35a676527d040e079909b159342798a00f2491dc7b2`. The standalone recovered `index.ts` file hash is `815d293aec1fd924efc83e5e8b5ea89b6b06ad292b0023fe4377ec61fb5093cb`; file and bundle hashes are different objects. The production patch remains staged pending explicit production approval after QA.

## Relax and remaining limits

Relax remains hidden. Categories 1745/1747 have 26/21 historical absent entries and no admitted release bindings. Do not treat absent import rows as proof of working upstream sources. Direct provider revalidation needs the designated account at the provider endpoint; its HTTP-only transport required a separate owner decision. No dead or speculative replacement channels are admitted. Existing source identities remain available for later deduplication/backup work.

No full EPG-source audit/rebuild or automatic backup-source engine is included. Physical Fire TV acceptance and production deployment are not complete.

## Physical Fire TV acceptance

- [ ] Synopsis stays compact with long text; live picture dominates.
- [ ] LEFT/RIGHT browse adjacent information without retuning; OK tunes; Back closes.
- [ ] Normal LEFT opens channel/category browser; normal RIGHT opens Recently Watched.
- [ ] CH+/CH− retains the playing channel's list context during information browsing.
- [ ] Every major category and sports folder icon is visually distinctive on TV.
- [ ] Sports US/UK priority and Kids/Movies priority are sensible.
- [ ] Guide matches Live Provider/A-Z/custom/favourites/history/catchup ordering.
- [ ] Saved channel/category moves persist; Guide focuses the playing row on open/return.
- [ ] Relax stays hidden unless working sources are independently restored and deduplicated.
- [ ] Sport Lounge discovery, idle rotation and preview focus behave correctly.
- [ ] Preview audio defaults audible; saved mute remains respected.
- [ ] Preview/full-screen transitions have one playback owner and one audible session.
- [ ] Remote Back/OK/D-pad have predictable contextual behaviour.
- [ ] Sustained Live playback is stable; VOD lip-sync is acceptable.
- [ ] Three/Māori/Te Reo play through the isolated QA gateway in the intended client/region.
- [ ] TVNZ 1/2 are tested only with a supported Widevine client and geographic access.
- [ ] A real current tennis event discovers evidence-backed candidates and plays through QA.

Keep PR #68 draft. Do not merge or describe the build as production-ready before physical acceptance.
