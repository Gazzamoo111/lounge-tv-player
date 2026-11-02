# Known issues

1. The exact locked proposal SQL files from local commits `8744b89` and `29a6b78` are not present in the remote GitHub branch or the mounted runtime. Release activation is intentionally blocked until they are recovered.
2. The current production/staging database has 3,287 taxonomy-visible Live channels, so it must not be used as a substitute for the locked 2,391-item Live v3 proposal.
3. Current `lounge-core-xtream` v28 authenticates against BEST, exposes provider IDs and has provider fallbacks. It must not be the universal customer endpoint after migration.
4. Current `lounge-core-live-m3u` v33 can fall back to the provider playlist and embeds provider stream IDs in customer URLs. The new staging worker/function pair removes that behaviour.
5. Current `lounge-core-vod-taxonomy` performs refresh/mutation work on GET and exposes provider item IDs. It should remain internal and not be used as the customer catalogue endpoint.
6. TVNZ/Freeview remains on the existing implementation. No new TVNZ source or Widevine work is part of this build.
7. The `lounge_delivery` schema and both staging Edge Functions are deployed in the existing Supabase project, isolated by release state. Cloudflare staging routing and real Android/player network QA are still pending. The release remains `draft` until the exact locked proposal rows are restored.
