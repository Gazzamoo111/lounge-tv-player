# Lounge TV finishing pass — QA checkpoint

Production/main and deployed backend functions are unchanged. This checkpoint starts at QA #628 / `ee99a82bfb83941a081a6ae5f66b1f6135afa30a` on PR #68. PR #70 was inspected as reference and not merged. The pinned core remains `1de78b1e2690c22b50c060e986bce221454bf880`.

## Implemented

- Six-hour deterministic Home discovery windows; bounded curated movie/series searches start at different editorial positions, and Trending order rotates without focus-driven shuffling. Visible Home checks cache expiry once per minute. History/favourites are retained.
- System PPV visibility filters for Live TV, customer search (including programme results), Home and Guide. Source rows and the independent sports resolver remain available. Saved favourites/custom lists do not override the system PPV policy.
- Shared category preference configuration prioritises US Sports then UK Sports. Selected Kids & Family → Movies uses one Room query for explicit moves, curated actual Disney/Pixar services, current EPG, standard feeds, 24/7 and backups. Normal provider/A-Z fallback remains available inside tiers. Existing manual move precedence is retained.
- Vector category marks, including fern/kangaroo/film/trophy styles. No platform emoji added.
- Audible Live/Sports preview default for unset preferences; explicit saved mute remains respected.
- Existing Sport Lounge retained. Idle hero rotation waits 20 seconds after remote input and pauses on preview focus. Right from the hero targets the preview. Selecting a lower event returns to the top before preview focus. Preview OK starts the chosen feed or expands the matching playing feed. Irrelevant top-right status controls are hidden on Sport Lounge.
- Adjacent-channel information browsing in the existing synopsis panel retains playback until explicit confirmation. Recently Watched remains. Information browsing and CH+/CH− have distinct lists; surf uses the playback list.
- OwnTV interrupted XMLTV read-error handling from `991118af` is ported through the existing pinned-core patcher. A failed download does not trigger completed-feed pruning or promote an incomplete cache.
- OwnTV competition/broadcaster phrase matching from `eb5e09de` is adapted to the existing catalogue, preserving Lounge fixture evidence and the `@` fixture delimiter. Numeric competition identities, wider competition names and unrelated broadcaster-owned channels are distinguished.
- The focused Guide row alone receives the active cursor/cell state (selective reference-port from PR #70), reducing cursor-driven row invalidation.
- Poster cache is bounded to 15%/64 MiB on normal devices and 10%/32 MiB on low-RAM devices. Low-RAM crossfade is disabled. Heap-cap tests accompany the change; physical Fire TV memory/scrolling acceptance is still needed.

## Automated evidence

Local checks: 25 Python guardrail/SQLite ordering tests, 10 sports gateway tests, four staged NZ/tennis gateway contracts, nine shared EPG repair tests, app/core literal inventories, clean pinned-core patch application, and gateway patch applicability.

QA #664 compiled both variants. App tests passed 336/337; the sole failure was the icon set's old equality expectation, corrected to include the three new vector paths. All 15 existing emulator tests passed; the interrupted XMLTV test passed. The surf test selector was corrected to use its translated resource instead of an incorrect English label.

QA #665 passed all 18 emulator tests, including adjacent information without retuning, interrupted XMLTV failure propagation, and D-pad movement from event to preview without changing the event. Its regression job stopped at the newly added EPG tests because CI lacked their existing `lxml==5.4.0` dependency; the workflow now installs it. This is intermediate evidence, not final APK acceptance.

Final signed APK identity and the final CI run must be recorded after all gates pass. Synthetic Exo/MPV probes do not establish provider stream quality, Fire TV lip-sync or sustained playback.

## Backend findings and staged repairs

`qa/gateway/README.md` documents the exact v50 NZ gateway defect and reviewable patches. The released MJH NZ synthetic IDs were incorrectly redirected to BEST. The clear Three/Māori/Te Reo manifest GETs returned HTTP 200. TVNZ 1/2 require Widevine and returned geographic restrictions from this test host. M3U DRM metadata helps compatible players; it cannot add Widevine support to Max IPTV or remove geographic requirements.

The tennis gateway restriction was re-checked. Categories 2176/2403 contain 130 present inventory rows; 79 have tennis names omitted by the current name predicate and are admitted by the staged predicate. Other rows are not broadly admitted without evidence. Neither NZ nor tennis production routing has been changed.

Relax groups are `4K RELAX UHD 3840P` (provider category 1745) and `4K RELAX 1920P` (1747). Current inventory marks all 26/21 entries absent; no corresponding live-core source bindings were found. Restoring customer cards now would advertise stale sources. Re-import/probe these groups before release admission, exact deduplication and primary/backup selection.

## Shared EPG coverage audit

Snapshot audited at `2026-11-11T06:47:55.335914+00:00`. Input: `epg-data/epg/guide.xml.gz`, SHA-256 `fe456279da122c0076f625f4a820d2d4f92d6b9bd7b0f9d25b1874477ec1fcad`. Active release: 2,188 non-PPV channels. Keys follow the gateway's effective ID/canonical-key logic, not just the raw `epg_id` column. Provider short-EPG and client supplemental guides are excluded, so these figures are **shared-feed coverage**, not total on-device coverage. US/UK counts overlap editorial groups and include only confidently identified original source prefixes.

| Group | Channels | Current/future | Current % | All seven calendar dates* | No data for effective key |
|---|---:|---:|---:|---:|---:|
| New Zealand | 14 | 0/0 | 0.0% | 0 | 0 |
| Australia | 38 | 11/11 | 28.9% | 11 | 12 |
| US | 917 | 317/317 | 34.6% | 245 | 576 |
| UK | 381 | 160/160 | 42.0% | 105 | 208 |
| Sports | 233 | 76/76 | 32.6% | 48 | 150 |
| Entertainment | 750 | 288/288 | 38.4% | 123 | 441 |
| Movies | 121 | 62/62 | 51.2% | 46 | 55 |
| Kids & Family | 67 | 22/22 | 32.8% | 13 | 41 |
| Documentaries & Lifestyle | 58 | 12/12 | 20.7% | 6 | 35 |
| Streaming | 235 | 0/0 | 0.0% | 0 | 234 |
| 24/7 | 672 | 672/672 | 100.0% | 672 | 0 |

*Calendar-date presence is not proof of uninterrupted coverage. No channel in this snapshot reached a full 168-hour future horizon from audit time. The 672 24/7 entries are transparent continuous-stream labels, not invented individual film/episode schedules. NZ shared schedules stop on 7 November; the currently scheduled AU/24-7 job does not refresh NZ. The core programme table contains only five old rows and is not evidence that the separate published XMLTV pipeline is current.

The interrupted-download patch prevents loss; it does not create missing IDs or schedules. NZ source refresh, verified matching, source-priority/merge audits, an updated release manifest and live end-to-end coverage measurement remain necessary. No speculative EPG mappings or published guide edits were made.

## Outstanding implementation / verification

- Authenticated third-party NZ API/M3U/segment decoding and non-NZ control tests on a designated QA line; isolated backend deployment and review before production approval.
- Material EPG coverage repair and seven-day refresh verification across all sources.
- Relax re-import/admission and controlled primary/backup-source failover for logical channels/events. Existing engine/format fallback and bounded decoder retry remain; they are not a multi-provider backup-source implementation.
- Full Guide ordering parity for every user/custom ordering mode; selected channel ordering and Guide cursor performance are improved, but the entire ordering contract is not claimed complete.
- Physical profiling of loading CPU, large-catalogue background work, cache use, Home/Sports/Guide scrolling and transitions. Do not infer performance from compilation.
- Prior Zootopia artwork and Ocean's Eleven lip-sync findings still require real stream/device evidence. No buffer, global audio-offset or default VOD-engine workaround was applied.

## Fire TV acceptance — only after automated gates pass

- Confirm Home changes across a six-hour window and remains smooth while scrolling.
- Confirm PPV is absent from customer Live/search/Guide and usable by Sport Lounge only for an appropriate event.
- Check US/UK Sports category order, Disney/Pixar priority, saved channel moves and all category vectors.
- Check NZ live playback and guide listings; also use the same designated line/non-NZ control in third-party clients after the staged backend repair is separately accepted.
- Check preview audio defaults on, saved mute works, and transitions never create two audible sessions.
- Leave Sport Lounge idle; interact to pause rotation; move right to preview; select a lower event and verify the whole preview remains visible; OK expands it.
- Confirm Follow controls have no top-right overlap.
- While Live plays, browse adjacent synopsis/now/next/start/end/progress without retuning; explicitly confirm to tune; verify Recently Watched and CH+/CH− context.
- Check Guide return focus/preview, scrolling and saved order against Live TV.
- Test VOD lip-sync, sustained Live/VOD playback, preview/full-screen transitions and remote Back/OK/D-pad.
- Relax and primary/backup-source failover remain blocked implementation items, not acceptance claims for this checkpoint.

Keep PR #68 draft. Do not merge, publish a production release or call this production-ready before the outstanding work and physical acceptance are complete.
