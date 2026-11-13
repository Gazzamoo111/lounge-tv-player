# Lounge TV | QA release build and server acceptance | 14 November 2026

**Status: INACTIVE DRAFT, BACKEND VALIDATED, NO-GO FOR CUSTOMER DEPLOYMENT PENDING PHYSICAL FIRESTICK QA.**

## Release

- Project: `liqocisegeqzbyuqloql`
- QA release ID: `53a5174a-3247-42b6-9384-1a83ff4d29f7`
- Release key: `live-3252-20261114-qa-draft`
- State: `draft`, intentionally **not active**
- Current active release remains `633c6458-0201-4a66-95f7-32455a1be086`, key `ppv-curated-817-20261106-qa`
- No production Edge Function was replaced. The existing customer Xtream endpoint still uses the old active release.

## Contents and stable IDs

| Content | In QA release |
| --- | ---: |
| Live | **3,252** |
| Movies | **5,142** |
| Series | **3,395** |
| Regular Live PPV | **0** |
| Live categories | **9** |
| Live category/subcategory pairs | **43** |
| Primary Live source bindings | **3,252** |
| Additional source variants / backups | **34** |
| Shortlisted BEST source IDs present in bindings | **327 of 327** |
| Known pre-existing customer Live IDs preserved | **800** |
| New globally reserved external Live IDs | **2,452** |

- `lounge_delivery.validate_release('53a5174a-3247-42b6-9384-1a83ff4d29f7')`: **`ok=true`**, all declared actual counts match expected. Missing primary sources, missing external IDs, missing Live category memberships, duplicate Live canonical keys, duplicate VOD IDs: **all zero**.
- Existing movie and series rows, posters, backdrops, editorial metadata and non-Live category memberships were compared with the active release using two-way set differences. **Both directions produced zero differences.**
- Primary source per Live channel is exactly one. The 34 backups correspond only to approved same-channel variants (Australian 9Now regional streams, MeTV, TCM, Disney+ feed variants and the other selected canonical variants). Distinct beIN stations, CBS Sports Golazo and other genuinely distinct networks remain separate channels.
- The five NZ Freeview primary bindings remain **MJH NZ** with IDs `900000001` to `900000005`. In particular TVNZ 1 and 2 have their Widevine-capable routing unchanged. This **does not assert that DRM playback works on a particular Firestick**.
- Sport Lounge's authenticated `/sport` route checks the BEST inventory and provider entitlement independently of the normal Live release, so no ordinary-Live PPV does not remove that separate event routing.

## Secure QA-only contract endpoint

New Supabase Edge Function: `lounge-live-release-qa`.

- Code on branch: `backend/catalogue/loung-live-release-qa_20261114.ts`
- This is **not** a public customer Xtream endpoint. It requires the existing internal worker token and reads only release `53a5174a-3247-42b6-9384-1a83ff4d29f7`. It never accepts an arbitrary release ID or activates anything.
- `qa_summary`: HTTP 200, release `draft`, 3,252 Live, 5,142 movies, 3,395 series, 43 folders, no PPV.
- `get_live_categories`: HTTP 200, **43 unique Xtream-format presentation category IDs**.
- `get_live_streams`: HTTP 200 with expected stream IDs, channel names, category IDs, artwork, EPG IDs and primary source identity for NZ TV (12), AU TV (19), US Major Leagues (10), UK Sports (35), US Sports (88), Cinemania (60) and Netflix (60).
- `get_short_epg`: HTTP 200 with six listings for each of TVNZ 1, 9Go!, 9Life, 9Rush and Australian beIN Sports 1. The shared EPG database returned **zero** for NFL Network in the QA-only endpoint; the existing customer backend has additional file/provider fallback not replicated in this preview, so full NFL EPG remains an acceptance item, not an asserted failure.
- A request without the internal token returned **HTTP 401**.
- Priority order spot-checks: NZ's TVNZ 1/2, Three, Whakaata Māori and Te Reo appear first; the ten main US sport stations are in approved order; AU TV frontloads Seven, Channel 9, Ten, ABC, SBS and two major news stations; Cinemania Hollywood channels sort numerically 1–50, followed by Premiere 1–10.
- Corrected a title spelling error from `3 BODY PROPLEM` to `3 Body Problem` in staging and the QA draft. Assigned a matching FanDuel-branded logo to the previously unartworked `FanDuel TV Extra` QA channel.
- The official customer endpoint was **not** switched to this preview. No QA login credentials or provider secrets are present in GitHub or its payloads.

## Gate results and limitations

- Read-only QA script `backend/catalogue/live_release_acceptance_20261114.sql`: **24 PASS, 1 BLOCK**.
- BLOCK: **Real Firestick playback**. The connected cloud environment cannot reach the user's private LAN / Firestick ADB endpoint, and no Firestick installation, control, decoder test, audio test or app-navigation test actually occurred.
- Source transport tests from Step 2 are separate: 327/327 MPEG-TS first-packet checks passed, but first packet is not proof of correct programme, codec, audio, lip-sync, sustained playback or the Firestick's Widevine capability.
- Most continuous 24/7 and streaming-labelled channels do not have programme-level EPG IDs. Do not create invented programme titles. Australian beIN 2 and a few US channels also need EPG acceptance against customer playback.
- Current active release has 825 PPV items in ordinary Live. The draft removes them, but production will not change until deliberately activated. Retain the old release ID for rollback.

## Release decision

**Do not activate.** The structural release and QA API are ready for device acceptance. Real device tests still need to check NZ/Freeview/TVNZ DRM, Australian broadcast playback and EPG, US major-sports content identity, stream switching, subtitles/audio sync, 24/7, Cinemania, cache refresh and category focus/Back navigation. Any failure must be corrected in a new draft before customer launch.
