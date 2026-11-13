# Lounge TV | Live catalogue Step 2 source-validation report

**Date:** 14 November 2026. **Environment:** Private Supabase staging. **Verdict:** Source transport QA completed; customer release remains blocked pending device/playback acceptance and PPV removal from ordinary Live.

## Staged totals

- 3,252 unique staged customer channels, reconciled by canonical key.
- 2,961 existing core taxonomy records retained.
- 293 shortlisted canonical source targets, two of which already exist in the retained core. This produces 291 net additions.
- 327 shortlisted physical provider source IDs mapped to the 293 proposed target keys.
- 9 top-level categories, 43 nonempty planned subcategories, no duplicate customer-facing normalized labels in the combined stage.
- Active customer release remains 3,013 live records, including 825 records in normal Live PPV. No activation, customer APK modification, or release mutation occurred.

## Real network transport checks

- Source probe endpoint: lounge-live-candidate-probe, deployed as new isolated authenticated Supabase Edge Function; it is not on the customer request path.
- Source: backend/catalogue/live_candidate_probe_20261114.ts on feature/live-catalogue-rebuild-20261113.
- Results stored in private lounge_core.live_candidate_probe_evidence_20261114. New function is restricted by existing metadata-enrichment worker token hash in lounge_core.worker_auth; no provider credentials are sent to clients or returned by probe responses.
- 327 of 327 shortlisted stream IDs tested successfully. Each returned HTTP 200 and a recognizable MPEG-TS packet stream in its first response. 0 transport failures.
- First-response latency mean 970 ms; p95 1,497 ms; slowest 2,448 ms; sampled payload bytes about 2.26 MB across all candidate IDs.
- Provider BEST inventory records matched the originally shortlisted 249 sources with no missing current-inventory rows, group/ID/name/artwork mismatch or adult flag. 78 legacy sports additions also reference present BEST inventory records.
- IMPORTANT: MPEG-TS packet evidence does not prove the correct programme/channel is playing, video/audio tracks decode without errors, lip-sync is correct, or Firestick/LG webOS navigation and playback work. Stream data can change. Do not describe these candidates as real-device validated.
- BEST stored inventory last observed 9 November 2026; rerun inventory refresh before the actual launch.

## Existing sports coverage reconciliation

- Identified a release regression: previous active release had 233 Sports entries, many of which were single-event streams or redundant aliases. The rebuild originally included only 55 channels in its central Sports group plus NZ/Australian sports.
- Preserved 68 additional perennial US/UK networks and 10 other specialty sports networks (F1, MotoGP OB1, DP World Tour, Tennis Channel 2, MLB Strike Zone, NESN Plus, SportsNet Dodgers, SportsNet New York, SportsNet Pittsburgh, SportsNet Pittsburgh Alternate) in isolated source staging, each with successful transport test.
- US/UK network disposition was 68 staged, 15 recognized aliases, 19 old/rebranded network names, and 7 out-of-scope/generic channels. Stable specialty channels added separately. Event-only/dated NBA, NFL, rugby and combat feeds remain managed by Sport Lounge, not permanently duplicated in ordinary Live.
- The major US Sports feature ordering remains NFL Network, NFL RedZone, NBA TV, MLB Network, NHL Network, ESPN, ESPN2, FS1, FS2 and CBS Sports Network.
- Critical source-identity warning for the new release builder: beIN Sports US 2, 4, 5, 6, 7 and 8 each have their own station and provider stream. They cannot be delivered as mere backup source variants for the single existing 'beIN Sports US' channel. CBS Sports Golazo is not CBS Sports Network, and FanDuel TV Extra is not necessarily an interchangeable source for FanDuel TV. Preserve distinct IDs in the release bindings.
- The status of old/rebranded network content has not been decoded/verified; their explicit exclusion is an editorial release decision, not evidence that those source endpoints are dead.

## EPG metadata cross-check

- Australia 9Go!, 9Life and 9Rush were linked to existing, currently fresh shared EPG IDs live:au-9go (210 listings), live:au-9life (235 listings), live:au-9rush (189 listings). Sample schedule titles match the respective station genres. Eight regional candidate source variants share each canonical channel; actual selected-region programme differences require device QA.
- Removed three incorrect or unsupported mappings: BBC Brit to Yesterday.uk; MeTV Plus to MeTV's WMEU4.us; and beIN Sports 2 US to the generic 'liga' ID.
- Confirmed MeTV Toons has correctly labelled animation schedule under MeTV.Toons.us2. The alternative MeTvToons.us ID displayed classic drama titles and must not be substituted.
- Australia beIN Sports 1 and 3 have fresh shared schedule records; Australia beIN Sports 2's provider EPG ID has no corresponding current shared schedule. 10 Shake has no verified shared schedule. Streaming/24-7 branded feeds generally have no genuine individual-programme EPG; do not invent programme titles.
- Having a shared EPG row does not prove an EPG is displayed correctly by a customer device. The existing customer EPG issues are not declared fixed by this audit.

## Current gate status

- Read-only gate suite: 20 PASS, 2 BLOCK after this checkpoint.
- BLOCK: all proposed new sources device-playback verified, since transport-only verification is not decoder and navigation acceptance.
- BLOCK: zero PPV in published Live, because the active (unchanged) release still has 825 PPV rows.
- No customer-side release created or activated; the current installed Firestick catalogue is unchanged.

## Next release actions

1. Construct an isolated QA delivery release from the 3,252 planned unique channel keys, preserving source identity and VOD, correctly binding the 327 probed sources and existing retained sources.
2. Resolve existing-source alternate mappings and popularity order. Move PPV/event feeds out of normal Live without interrupting Sport Lounge event discovery.
3. Run database release validator and authenticated Xtream category/live-stream/short-EPG endpoint checks against the QA release.
4. Test representative NZ, Australian, UK, US, sports, 24/7, Cinemania and streaming channels on Fire TV/Android and LG webOS, including audio, codec, black-screen delay, back navigation and source identity.
5. Activate only after acceptance. Keep the old active release as a rollback reference.
