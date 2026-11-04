# Read-only guide audit, 5 November 2026 NZ

No guide mapping or production state changed. Frozen catalogue/guide rows are in backend/supabase/functions/lounge-sports-candidates/test/fixtures/catalogue-audit.json. Every audited service has zero programmes in the database now-to-24-hour window. This does not establish stream availability or actual content.

## Ownership traced
Production lounge-core-epg-sync v10, bundle a90102b0697ff1d5d4ceea3fe2e0802cd59bfa1a33b3946c6b7ab000211f9225, read through the source retrieval API without invocation. Its owner source is not versioned in this Android checkout.

- Lines 81-108 select active lounge_delivery.catalogue_releases, live_release_items and primary live_source_bindings, joined to lounge_core.live_channels.
- Canonical guide precedence: core channel epg_id, release item epg_id, source binding epg_id.
- Source guide precedence: binding epg_id, release item epg_id, core epg_id.
- Rows without any guide ID are excluded before sync.
- Lines 131-132 request BEST get_short_epg for the binding's provider_stream_id.
- Lines 147-214 preserve missing/error/empty/ok state in live_epg_state and programmes in live_epg_programmes. Source type is best_short_epg, not best.
- Lines 200-201 fill a missing canonical epg_id from the source guide ID.

A request to the sync endpoint would write live tables and call BEST. It was not used as a read-only test.

## Before/after mapping manifest
All IDs and mappings in the frozen snapshot remain unchanged. AU beIN1/2/3 are present with beINSports1/2/3.au but no state rows. UK-labelled AU variants have null guide IDs and no state. Seven additional UK-labelled variants retain their differing .qa IDs; their actual region/content is unknown. Fox501 has no state; Fox502-507 have explicit empty states. Kayo501-507 have no state. All video observations, actual channel bugs, programme identity, measured resolution/fps/bitrate/audio and buffering remain unverified.

No number-based remap is justified. Coverage failure must be resolved in isolated guide ingestion/release bindings with actual content evidence, not by inventing a guide inside the Android app. Recovery manifest is therefore empty: there are no changed mappings to revert.
