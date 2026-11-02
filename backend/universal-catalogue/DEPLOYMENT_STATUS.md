# Universal Catalogue Staging Deployment Status

Date: 2026-11-02

## Deployed

- Supabase schema: `lounge_delivery`
- Release: `universal-v1-staging`
- Release state: `draft`
- Expected Live KEEP: 2,391
- Expected Movie KEEP: 5,142
- Expected Series KEEP: 3,395
- Staging catalogue Edge Function: `lounge-catalogue-staging` v2
- Staging playback Edge Function: `lounge-source-resolver-staging` v2
- Both Edge Function health endpoints return HTTP 200.
- Provider credentials remain server-side; customer metadata and playback URLs use Lounge opaque IDs.

## Release lock

Current release rows are intentionally empty:

- Live actual: 0 / 2,391
- Movies actual: 0 / 5,142
- Series actual: 0 / 3,395

`lounge_delivery.stage_release()` and `activate_release()` reject the release until all exact locked KEEP counts are present.

## Still required

Recover the exact local-only proposal SQL from:

- `8744b89` -> `backend/live-catalogue-v3/proposed-canonical-live.sql`
- `29a6b78` -> `backend/vod-catalogue-v1/proposed-canonical-vod.sql`

VOD proposal SHA-256 must be:

`c2239f8195c56fe93d94b265ebf7862c8b7e2b3377af6dbe8645c98b05587d18`

Do not substitute the current 3,287 taxonomy-visible Live rows.

## Not changed

- Production customer routing
- Existing customer credentials
- Production Lounge app
- Existing production Xtream/M3U endpoints
- TVNZ/Freeview behaviour
