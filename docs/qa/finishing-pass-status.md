# Lounge TV finishing pass — QA checkpoint

Production/main and deployed backend functions are unchanged. This next pass starts from the accepted QA #667 checkpoint / `483e020734f6dd0fb0cf1c9eb9ec94fd100d56ce` on draft PR #68. PR #70 remains reference-only and is not merged. The pinned core remains `1de78b1e2690c22b50c060e986bce221454bf880`.

## Implemented

- Six-hour deterministic Home discovery windows; bounded curated movie/series searches start at different editorial positions, and Trending order rotates without focus-driven shuffling. Visible Home checks cache expiry once per minute. History/favourites are retained.
- System PPV visibility filters for Live TV, customer search (including programme results), Home and Guide. Source rows and the independent sports resolver remain available. Saved favourites/custom lists do not override the system PPV policy.
- Shared category preference configuration prioritises US Sports then UK Sports. Selected Kids & Family → Movies uses one Room query for explicit moves, curated actual Disney/Pixar services, current EPG, standard feeds, 24/7 and backups. Normal provider/A-Z fallback remains available inside tiers. Existing manual move precedence is retained.
- A deterministic vector category system now gives every major Live section its own mark and distinguishes important customer folders including US/UK Sports, rugby union/league, cricket, football, motorsport, combat sports, basketball, NFL, hockey, baseball, tennis, golf, PPV, movies, series, kids, documentaries, lifestyle, 24/7 and Relax. All marks use the same 24×24 line language; no platform emoji or generic person icon is used for Kids. Automated uniqueness and SVG-path parsing tests cover the assignments.
- Audible Live/Sports preview default for unset preferences; explicit saved mute remains respected.
- Existing Sport Lounge retained. Idle hero rotation waits 20 seconds after remote input and pauses on preview focus. Right from the hero targets the preview. Selecting a lower event returns to the top before preview focus. Preview OK starts the chosen feed or expands the matching playing feed. Irrelevant top-right status controls are hidden on Sport Lounge.
- The Live synopsis overlay is now a compact, single-focus information browser rather than a second control dashboard. It shows the channel logo/name/number, current title, New Zealand start/end time, progress, concise synopsis, next programme and technical badges. LEFT/RIGHT browse adjacent information without tuning; OK alone changes channel; UP opens Guide; DOWN opens playback controls; Back closes. The embedded Previous/Watch/Next controls, oversized artwork and Recently Watched carousel were removed. Standard hidden-HUD LEFT channel-list and RIGHT history access remain unchanged.
- OwnTV interrupted XMLTV read-error handling from `991118af` is ported through the existing pinned-core patcher. A failed download does not trigger completed-feed pruning or promote an incomplete cache.
- OwnTV competition/broadcaster phrase matching from `eb5e09de` is adapted to the existing catalogue, preserving Lounge fixture evidence and the `@` fixture delimiter. Numeric competition identities, wider competition names and unrelated broadcaster-owned channels are distinguished.
- The focused Guide row alone receives the active cursor/cell state (selective reference-port from PR #70), reducing cursor-driven row invalidation.
- Guide `Live TV` ordering now consumes the same selected Live rail snapshot: provider or A–Z mode, explicit manual moves, editorial tiers, custom categories, Favorites, History, Catch-up and the NZ first-five rule. Explicit Guide-only Provider/A–Z/Catch-up/Favorites choices remain independent. Opening or returning to Guide focuses the channel that still owns playback without retuning it.
- Poster cache is bounded to 15%/64 MiB on normal devices and 10%/32 MiB on low-RAM devices. Low-RAM crossfade is disabled. Heap-cap tests accompany the change; physical Fire TV memory/scrolling acceptance is still needed.

## Automated evidence

Local next-pass checks: 25 Python patcher/guardrail tests, five staged NZ/tennis gateway contracts, the hardcoded-string inventory, XML resource parsing, clean patch whitespace and clean applicability of the new pinned-core Guide-order patch. Android compilation, JVM tests and emulator tests are authoritative in GitHub Actions because the local worker does not carry the repository's Gradle 9.7.1 distribution.

QA #664 compiled both variants. App tests passed 336/337; the sole failure was the icon set's old equality expectation, corrected to include the three new vector paths. All 15 existing emulator tests passed; the interrupted XMLTV test passed. The surf test selector was corrected to use its translated resource instead of an incorrect English label.

QA #665 passed all 18 emulator tests, including adjacent information without retuning, interrupted XMLTV failure propagation, and D-pad movement from event to preview without changing the event. Its regression job stopped at the newly added EPG tests because CI lacked their existing `lxml==5.4.0` dependency; the workflow now installs it. This is intermediate evidence, not final APK acceptance. The runner's newer default Python also lacks a wheel for the generator's pinned lxml; regression now explicitly selects Python 3.12 and a binary lxml wheel, matching the existing EPG workflow.

QA #665 exported synthetic emulator measurements: poster navigation input-to-idle p50 77 ms / max 263 ms over 12 inputs; local Exo startup 1,095 ms and MPV startup 1,503 ms; zero recorded post-start buffering transitions for both. Process PSS was 234,288/248,886 KiB. These are labelled local emulator probes and do not measure Fire TV scrolling or A/V output drift.

Final signed APK identity and the final CI run are recorded in the current PR #68 description after all gates pass. Synthetic Exo/MPV probes do not establish provider stream quality, Fire TV lip-sync or sustained playback.

The next-pass CI run and signed ARM QA identity will be appended here only after the full workflow passes. Until then, synopsis layout, category rendering and Guide focus/order remain implementation-complete but device-unverified.

## Backend findings and staged repairs

`qa/gateway/README.md` documents the exact v50 NZ gateway defect and reviewable patches. The released MJH NZ synthetic IDs were incorrectly redirected to BEST. The clear Three/Māori/Te Reo manifest GETs returned HTTP 200. TVNZ 1/2 require Widevine and returned geographic restrictions from this test host. M3U DRM metadata helps compatible players; it cannot add Widevine support to Max IPTV or remove geographic requirements.

The tennis gateway restriction was re-checked. Categories 2176/2403 contain 130 present inventory rows; 79 have tennis names omitted by the current name predicate and are admitted by the staged predicate. Other rows are not broadly admitted without evidence. Neither NZ nor tennis production routing has been changed.

Relax remains intentionally hidden. Provider categories 1745 (`4K RELAX UHD 3840P`) and 1747 (`4K RELAX 1920P`) contain 26 and 21 historical rows respectively, all absent since 18 October 2026. They are no longer present in the tracked category catalogue, have zero release source bindings and zero admitted release items as of inventory poll 637 (latest live inventory timestamp 9 November 2026 04:06 UTC). Current names containing nature/ambient/scenic are ordinary documentary, motorsport or streaming channels—not replacement Relax groups. Restoring a customer card would therefore advertise dead or misclassified sources. Re-import/probe genuine Relax groups before release admission, exact deduplication and primary/backup selection.

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
- Physical Fire TV verification that Guide order matches Live TV in provider and A–Z modes, including custom categories and saved moves, and that Guide opens on the playing row without retuning. Automated ordering contracts are implemented; device focus/scroll behaviour is not yet accepted.
- Physical profiling of loading CPU, large-catalogue background work, cache use, Home/Sports/Guide scrolling and transitions. Do not infer performance from compilation.
- Prior Zootopia artwork and Ocean's Eleven lip-sync findings still require real stream/device evidence. No buffer, global audio-offset or default VOD-engine workaround was applied.

## Fire TV acceptance — only after automated gates pass

- Confirm Home changes across a six-hour window and remains smooth while scrolling.
- Confirm PPV is absent from customer Live/search/Guide and usable by Sport Lounge only for an appropriate event.
- Check every major Live section and important customer folder uses the intended distinct line icon. Verify US/UK Sports category order, Disney/Pixar priority and saved channel moves; no unrelated categories should share a generic TV/person mark.
- Check NZ live playback and guide listings; also use the same designated line/non-NZ control in third-party clients after the staged backend repair is separately accepted.
- Check preview audio defaults on, saved mute works, and transitions never create two audible sessions.
- Leave Sport Lounge idle; interact to pause rotation; move right to preview; select a lower event and verify the whole preview remains visible; OK expands it.
- Confirm Follow controls have no top-right overlap.
- While Live plays, open the compact synopsis with OK. Confirm logo/name/number/current title/NZ start-end/progress/synopsis/next/technical badges fit without overlap. Browse LEFT/RIGHT without retuning, press OK to tune, use UP for Guide, DOWN for playback controls and Back to close. Then hide the HUD and confirm LEFT still opens the channel list and RIGHT still opens history.
- Open Guide from Live and from the synopsis. Confirm focus lands on the currently playing row without changing playback. Compare provider order, A–Z order, custom categories and saved manual moves against Live TV.
- Test VOD lip-sync, sustained Live/VOD playback, preview/full-screen transitions and remote Back/OK/D-pad.
- Confirm no Relax customer area is shown. This is intentional until a genuine current provider group, working streams and release bindings exist. Primary/backup-source failover also remains blocked and is not an acceptance claim for this checkpoint.

Keep PR #68 draft. Do not merge, publish a production release or call this production-ready before the outstanding work and physical acceptance are complete.
