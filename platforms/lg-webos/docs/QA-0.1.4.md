# QA 0.1.4: EPG recovery

## Confirmed findings

The public canonical feed was downloaded read-only on 7 November 2026 NZ time. TVNZ 1/2, Three and Whakaata Māori had no current listings: their last entries had already ended. Sky Sport had current entries. The main feed contained 1,187 channel declarations; its presence alone did not establish current coverage for the complete catalogue.

Fresh public NZ and Australia/Sydney XMLTV feeds were independently downloaded and inspected. The NZ feed supplied current entries for the four expired terrestrial channels. AU supplied current listings for Seven, Channel 9, 9Gem, 9Go and Ten. Both gzip URLs permit cross-origin fetching. Some other regional feeds tested were unavailable/blocked or too large for direct TV use; they are not silently enabled.

A read-only release roster showed Ten and 10 Bold both declaring 10Bold.au. They are distinct schedules. The previous device cache keyed by EPG ID would make such collisions share a single cached list.

## Implemented

Region-restricted fallback sources fill actual intervals absent from the canonical guide. These sources only match channels identified in the corresponding region by category or explicit EPG ID; NZ Bravo cannot populate US Bravo. Regional AU aliases are explicit, not fuzzy. The Ten identity overrides the known incorrect 10Bold.au declaration without modifying the catalogue or playback binding. Per-stream cache keys prevent collisions. Exact quality variants sharing a proven regional EPG identity still receive the same guide.

A future-only cache hit no longer prevents now-programme fallback. Returned lists are merged by real intervals, with preferred-source overlap priority. Manual user overrides never silently revert to the original gateway's guide identity. A 60-second negative retry window and a deduplicated two-request visible-row queue prevent repeated empty reads flooding the gateway.

If a refresh partially fails, the previous valid guide generation remains available for missing entries. The old generation is kept during pruning and is removed after a fully successful refresh. Settings shows source health and coverage at the latest refresh. Visible rows refresh once a minute without rebuilding the shell or touching playback.

The worker prunes unmatched programme records before decoding titles/descriptions, uses per-source timeouts, validates a complete XML source before publishing it, and retains actual clock offsets. A one-time guide-cache migration runs on first 0.1.4 startup. First launch can take longer while fresh guide data is prepared; later launches reuse the complete cache.

## Validation

49 domain/native lifecycle tests and 31 browser journeys pass. Added checks cover future-only data, merge priority, stream-cache collisions, AU Ten aliases, regional separation, manual override precedence, shared quality variants, real gzip decoding through the worker, source health and retention after a failed canonical refresh. Existing playback, navigation, VOD and Settings regressions remain covered.

A standalone browser worker was also run against the downloaded canonical XML plus actual NZ/AU gzip files and the read-only active-release roster. It parsed about 49,000 relevant programmes and produced current listings for the checked NZ/AU channels. This exercised actual feed bytes and matching, not physical LG rendering or stream-content verification. The roster includes event/rotating slots without normal published schedules; it is not a claim that every active LG account exposes the same channel count.

## Limits

The shared main XMLTV still contains missing/expired UK/US entries and guide-less PPV/rotating channels. This repair restores verified regional entries and prevents client-side loss. It does not guarantee a programme for every channel, prove that a stream carries the listed show, or repair the shared guide publishing pipeline.

The shared short-EPG fallback source was inspected read-only and has a malformed regular-expression escape replacement. The LG bulk guide path bypasses this. That shared API is not deployed as part of this isolated client change because it also handles production authentication/playback and requires its own acceptance test.

No Android code, database rows, customer catalogue/order, provider binding, shared production function or playback routing is changed. Device acceptance: compare the programme now on TVNZ 1/2, Three, Whakaata Māori, Channel 9 and Ten; verify Sky Sport still has listings; inspect guide source health and retain the 15-minute playback baseline.
