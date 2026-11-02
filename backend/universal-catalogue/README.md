# Lounge TV Universal Catalogue Delivery

## Contract

BEST inventory is upstream only.

`BEST -> lounge_sync -> lounge_core -> lounge_delivery release -> customer API/feed -> Lounge app / external player`

The customer-facing release owns admission, display names, categories, ordering and editorial status. Provider sync may update source availability, provider identifiers, source health and provider metadata, but cannot make a new item customer-visible or overwrite editorial decisions.

## Deliberate separation

- `public.*`: customer/subscription/service assignment and provider-account plumbing already present.
- `lounge_sync.*`: raw BEST inventory and change detection already present.
- `lounge_core.*`: canonical Live/VOD identities, metadata and source candidates already present.
- `lounge_delivery.*`: new release lock, Lounge credentials, opaque external IDs, category membership and playback-session accounting.

## Security correction

The current `lounge-core-xtream` path authenticates against BEST and exposes provider stream/item IDs. The new staging adapter never accepts BEST credentials as customer identity and never returns BEST IDs in metadata or playback URLs.

Xtream playback URLs contain Lounge credentials because that is how Xtream clients work. The IDs in those URLs are Lounge opaque IDs. Provider credentials and provider stream/item IDs are resolved only after authentication on the backend.

## Release safety

The initial release is seeded as `draft` with exact expected counts:

- Live: 2,391 KEEP
- Movies: 5,142 KEEP
- Series: 3,395 KEEP

Activation is blocked unless all three exact counts are present and every visible item has an opaque Lounge ID. This prevents today's drifting provider/canonical state from becoming the customer catalogue by accident.
