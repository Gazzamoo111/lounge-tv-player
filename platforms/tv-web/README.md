# Lounge TV — LG webOS / Samsung Tizen port

This is the platform port of the actual Lounge TV Android application. ZUI is not a dependency.
Android 5.2.3.1 source commit and core 1.0.69 are pinned in `src/contract.json`.
The parent repository's GPLv3 licence applies; copied/translated OwnTV/Lounge source and notices must remain available with distributed builds.

## Scope and current increment

The target is complete feature and visual parity: Home/hero and all rails, Live TV, Sport Lounge, Guide, Movies, Series, Downloads, More, profiles, favourites/history, backups/sync, all twelve settings groups and their subpages. Nothing is implicitly dropped because it is Android-specific.

This first increment is **not a complete port or a customer candidate**. It establishes:

- Exact Stage palette/geometry and original Lounge brand/font assets, rather than another player's theme.
- Android rail order and settings groups extracted from Kotlin source, plus every screen/settings source unit with implementation status. Status is deliberately `not_ported` until the corresponding complete screen passes acceptance; a partial shell does not count.
- Sport Lounge's actual selection rules: Live now / Today / Upcoming, seven local dates, lifecycle deduplication, My Teams, distinct NFL/football categories and NZ/AU ordering.
- Native LG HTML video and Samsung AVPlay adapters, remote key registration and focus-only navigation.
- Xtream authentication, category-based live/VOD/series requests and media URLs; incremental 48-card rendering, episode browsing, basic session favourites/history.
- Twelve-group settings navigation, with only genuinely implemented preferences exposed as editable controls.
- Separate webOS and Tizen build directories. No public hosting or production release.

Still pending: precise screen-by-screen visual reproduction, Home artwork/Trending and all curation rules, sports feeds/match and league pages/channel matching/follows/reminders, full guide/XMLTV, complete search, profile persistence, secure credential persistence, all remaining settings, recordings/downloads/multiview capability mapping, error recovery, subtitle/audio controls and physical device validation. Browser playback is not TV playback validation. The small functional shell is not presented as a substitute finished layout.

## Development

```sh
npm ci --ignore-scripts
npm test
npm run build
node scripts/prepare-targets.mjs
```

Run `npm run dev` only for interface development. The packaged TV is required to prove native playback and HTTP-provider behaviour. `targets/webos` may be packaged with the LG CLI; Samsung additionally needs the customer's TV certificate/signing workflow. Do not distribute either before device acceptance.

Regenerate the source inventory from the pinned Android checkout and English XML resources from OwnTV_Core tag `core-1.0.69`:

```sh
CORE_STRINGS_DIR=/path/to/core-1.0.69/core/src/main/res/values npm run contract
```

## Performance and data rules

Remote movement changes DOM focus, not application state. Catalogues load per category, cache only within the session, and render bounded pages rather than mounting the whole provider library. No per-card timers, animated blur or smooth-scroll queues are introduced.

Development credentials remain in memory only, are never logged or embedded in DOM attributes, and are cleared on page exit. Settings persist only whitelisted values. No production Supabase connection, BEST panel request, subscription mutation, or customer record is part of this port. Existing Android and live onboarding remain untouched.

## Acceptance

Each feature needs (1) Android source mapping, (2) implementation, (3) behaviour tests, (4) visual comparison with the same real content, and (5) LG and Samsung device results. Platform limitations must be explicit and reviewed rather than hidden behind inert controls. Do not mark the port complete based on a successful build.
