# NZ gateway repair — staged, not deployed

The audited production edge is `lounge-core-xtream` version 50, SHA-256 `ec3385ec20c0c979214cc35a676527d040e079909b159342798a00f2491dc7b2`.

Its `/live` handler ignores the release binding's provider and redirects every stream ID to BEST. The active release binds five Freeview rows to MJH NZ using synthetic IDs 900000001–900000005. The Android app overrides these URLs locally; external clients cannot do that.

`gateway-nz-routing.patch` applies to that exact downloaded edge source. Copy `nz-routing.mjs` beside `index.ts`. The patch carries provider identity into the playback and M3U queries, authenticates the account before non-BEST routing, routes the three clear HLS sources to their existing public URLs, and preserves TVNZ Widevine metadata in compatible M3U output. Generic Xtream clients receive an explicit `drm_client_required` error for TVNZ rather than a fabricated BEST URL. This does not remove geographic or DRM requirements.

Validation performed: patch applicability against the retrieved v50 source; three Node routing/metadata contract tests; public-source GETs returned 200 for Three, Whakaata Māori and Te Reo manifests. TVNZ 1 and 2 returned 403 with “Access restricted based on geographic location” from this test host. Manifest HTTP 200 does not establish sustained decoding or segment playback.

Before production: deploy to an isolated QA function, run authenticated API/M3U/redirect and segment/decoder checks with a designated QA line in the intended region, test a non-NZ control channel in the same third-party clients, and obtain approval for production deployment. No customer credentials were retrieved or production functions changed.

`tennis-allowlist.patch` separately restores the missing `tennis` name predicate while preserving present-inventory and authenticated `/sport` checks. Read-only SQL confirmed that 79 present tennis-named channels in categories 2176/2403 fail the current name predicate and satisfy the staged predicate. Those categories contain another 51 present rows whose names are not proved by this change; they are not admitted by a broad category override. The patch changes eligibility only, not fixture evidence or a claim of playable content.
