# Lounge TV | Live catalogue rebuild | 13 November 2026

**State:** Internal staging only. **DO NOT activate or merge into production.**

This work continues the existing Lounge TV platform. The active Xtream catalogue must be sourced from `lounge_delivery.catalogue_releases`, **not** directly from `lounge_core.live_customer_taxonomy`. Production's active release is `633c6458-0201-4a66-95f7-32455a1be086` (`ppv-curated-817-20261106-qa`), with **3,013** kept Live records, **825** of them in an ordinary Live `PPV` category. The active release passes database integrity checks but does not meet the new curation specification.

## Durable internal staging

- `lounge_core.live_catalogue_rebuild_20261113`: original category/name/IDs, proposed category/name/sort, counts of linked source statuses, classification QA flags, duplicate target and hold reasons. All **3,369** enabled taxonomy records preserved.
- `lounge_core.live_rebuild_source_candidates_20261113`: provider inventory candidates, canonical target proposal, source artwork and EPG IDs, existing canonical target, probe and shortlist statuses. **241** source rows, **219** distinct proposed targets, **218** shortlisted provider source rows covering **206** target keys. None has been playback-verified by this rebuild.
- `lounge_core.live_rebuild_category_order_20261113`: editorial order for **nine top categories** and **54 intended subcategories**. All entries have `visible_on_qa=false` pending acceptance. Do not create empty folders in a release.

At this checkpoint **2,979** existing entries are stage candidates, **390** held, **34** potential duplicates held, and **2,932** candidates have a category/subcategory change in the proposed plan. **1,474** candidate assignments require further classification review. These are *not published changes*.

### Approved top-level editorial order

1. New Zealand
2. Australia
3. Sports
4. Entertainment
5. Movie Channels
6. Kids & Family
7. Streaming Channels
8. 24/7 Channels
9. Documentaries & Lifestyle

No Canada customer category until tested regional streams. Event/PPV feeds remain in Sport Lounge infrastructure and not in ordinary Live TV. No branded US feed is a substitute for missing Bravo NZ or HGTV NZ.

## Source-reconciliation findings

- New Zealand Bravo NZ and HGTV NZ are excluded placeholders with **zero** linked sources. No verified `NZ|` BEST inventory match found. Do not expose.
- 6 Irish channels held: RTÉ Jr, TG4, Virgin Media One, Two, Three, Four. 13 US news rows held.
- 47 older taxonomy PPV slots held; the active **3,013** release contains a **separate 825 PPV** entries. Do not confuse these two measures.
- BEST present Live inventory last observed 9 November UTC: **16,140** provider rows. The 13 November refresh changed the Lounge taxonomy, not the BEST source timestamp.
- Australian beIN Sports 1–3 found with EPG IDs; **10 Shake** found as 8 city variants that must resolve to **one** station with preferred/backup source order.
- Cinemania: **50** numbered Hollywood feeds, **10** Premiere feeds, **1** separator. Only the 60 actual provider entries are staged as candidates, not published.
- Classic entertainment and movies: MeTV/MeTV Plus/Toons, Antenna TV, FETV, Catchy Comedy, Buzzr, BBC Brit, BBC Lifestyle, ScreenPix, MoviePlex, Sony Movie Channel, Dove Movies, Family Movie Channel, FMC Family Classics. Keep a single canonical record per brand/channel and provider variants as backups. BBC Brit's provider EPG ID requires review.
- Streaming: discover Apple TV+, Disney+, Prime Video, Max, Paramount+, Hulu, Peacock, Netflix continuous-feed groups; never describe as official service account access. **Netflix cap:** 23 existing staged + 37 shortlisted new title feeds = 60; 23 extra numbered provider rows held from the shortlist.
- 9Go!, 9Life and 9Rush were not recovered from current present AU-labelled BEST inventory. Do not fabricate.
- Region/genre sorting is editorial, not based on current watch analytics.
- The `US Major Leagues` top 10 is locked to: NFL Network, NFL RedZone, NBA TV, MLB Network, NHL Network, ESPN, ESPN2, FS1, FS2, CBS Sports Network.
- Physical QA and runtime playback verification for **all newly shortlisted sources** are missing. A candidate/healthy status in database is not proof of live playback. EPG and logo metadata may be present without a playable stream.

## Critical implementation detail

`lounge_core.refresh_live_customer_taxonomy()` rebuilds its table from core channel/source rules. Direct manual taxonomy updates would be overwritten. The *customer* Xtream endpoint in `lounge-core-xtream` v52 selects the active `lounge_delivery` release, using `live_release_items` with `admission='keep'` and primary source bindings. Before customer rollout:

1. Review and correct the remaining 1,474 stage classification decisions, especially mixed 24/7 Shows, OnePlay, Movies & Series.
2. Probe shortlist sources, validate contents, logos, EPG, channel IDs, and region. Reject failures or misinformation. Do not silently attach UK/US feeds to NZ canonical keys.
3. Resolve duplicate candidates with explicit stable-ID aliases/favourite migration or equivalent source consolidation. Keep provider source history.
4. Build a **new draft** delivery release with canonical IDs, release item rows, primary source bindings, category and membership records. Preserve VOD and existing customer identity mapping.
5. Execute `lounge_delivery.validate_release(release_id)`, then authenticated QA Xtream `get_live_categories`, `get_live_streams`, `get_short_epg` and representative playback checks. Validate Android/Fire TV and LG webOS remote, focus, caches and refresh.
6. Check category and subcategory counts from the **QA release**, require no empty/underfilled folders without a documented editorial reason, verify no PPV/Irish/US news or duplicated channels.
7. Only after physical acceptance, use existing `lounge_delivery.activate_release(release_id)` process. Old active release must remain available for rollback. Verify published results and counts from authenticated customer endpoint after refresh. No production deployment has been approved in this staging pass.

**Current QA verdict: NO-GO for customer rollout.** Database stage structure passes core exclusion and sorting gates; playback verification, 24/7 classification review and actual new release delivery are incomplete.
