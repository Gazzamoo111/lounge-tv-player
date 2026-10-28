# Lounge TV — LG webOS / Samsung Tizen port

GPLv3 platform port of Lounge TV Android 5.2.3.1, pinned source/core inventory in src/contract.json. ZUI is not a dependency.

## Development build 0.2.0

Implemented flows: editorial catalogue-backed Home hero and rails, live/movie/series categories with bounded 36-card pages, VOD details and episodes, global search, favourites/history/resume per profile, twelve settings groups with validated implemented preferences, EPG and manual XMLTV import, real Sport Lounge fixture feeds and match channel suggestions, teams, native LG video and Samsung AVPlay adapters, audio/subtitle selection when exposed, aspect and sleep controls. Remembered Xtream sources use AES-GCM with a non-exportable IndexedDB key; unsupported storage falls back to session sign-in. Backups exclude credentials and provider URLs.

Not a complete Android port or customer release. Pending: exact Trending gateway and complete visual comparison, advanced settings, profile PIN enforcement, league standings, reminder notifications, local sync, recording/download/multiview mapping, physical LG playback/performance acceptance and Samsung signing/device tests. Reminder buttons save selections only; no alerts are claimed. Source contract statuses remain conservative until complete screen acceptance.

## Build and check

```sh
npm ci --ignore-scripts
npm run build
npm test
node scripts/prepare-targets.mjs
npx --yes --package @webos-tools/cli ares-package targets/webos -o packages
```

24 automated checks include production-bundle DOM boot/navigation, catalogue caching and credential-safe errors, bounded pagination, editorial matching, profile isolation/backup, sports timezone/filter/feed rules and native player adapter sequencing. DOM simulation and adapter tests do not demonstrate real TV playback or frame rate. Samsung target files require signing with the TV certificate before installation.

Existing Android app, live onboarding, Supabase, BEST accounts and subscriptions are untouched. Each complete screen still requires source mapping, behaviour checks, visual comparison and physical TV results. This build is for development testing only.
