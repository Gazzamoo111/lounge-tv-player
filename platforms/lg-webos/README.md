# Lounge TV for LG webOS

Isolated LG client, QA package `nz.loungetv.webos.qa`, version **0.1.1**. This is an initial installable implementation. Full Android feature and visual parity has not been accepted, and LG playback has not been physically verified.

Android reference: published **v5.2.3.10**, commit `44388fa8683df20a51ad5166f3ea73d7b08c8051`. Current `main` is older in application features, despite having newer documentation and EPG changes. See [parity matrix](docs/PARITY.md).

## Install the provided package

With the `.ipk` downloaded to the Mac's Downloads folder and the existing `loungetv-lg` device configuration:

```bash
cd ~/Downloads
ares-install --device loungetv-lg nz.loungetv.webos.qa_0.1.1_all.ipk
ares-launch --device loungetv-lg nz.loungetv.webos.qa
```

Sign in with an existing Lounge TV customer username and password. There is no server entry or test URL screen. No credentials are bundled. The application reads the current customer catalogue through `connect.loungetv.nz` and uses the current Lounge sports services.

## Build from source

Node 24 LTS. `npm ci` installs the pinned build dependencies. The project has no runtime npm dependencies.

```bash
npm ci
npm test
npm run package
```

Output: `artifacts/nz.loungetv.webos.qa_0.1.1_all.ipk`.

Browser regressions use Chromium and mock the real API contracts with a large catalogue. They do not establish actual provider playback or customer authentication:

```bash
npx playwright install chromium
npm run build
npm run test:browser
```

`PLAYWRIGHT_CHROMIUM_EXECUTABLE` can point to an existing Chromium executable. `npm run serve` serves the built application at port 4173.

## 0.1.1: startup and navigation repair

- A visible startup flow gates Home until authentication, all three catalogue libraries and initial guide preparation complete. Real counts are shown, with retry/change-login controls on failure.
- Catalogue and EPG persist in IndexedDB, scoped to the customer account. A fresh cache (six hours) is reused on restart; Settings refresh forces a fresh download. No password is stored in IndexedDB.
- Full XMLTV parsing runs in a dedicated Worker with bounded input size and timeout. A completed generation is published atomically; previous guide generations are pruned after a successful replacement. Missing/full-guide failures fall back to available short guides with an explicit notice.
- One persistent app shell. Completion of artwork/sports requests does not rebuild the UI. Scrolling reuses overlapping card nodes; metadata/progress reads are cached and requests deduplicated.
- Fixed 128 px icons-only sidebar and independent Live category accordion. All returned category IDs, including empty folders, remain visible.
- Launcher relaunch uses webOS default foreground handling. The former package incorrectly set `handlesRelaunch: true` without a matching handler.

## Implemented

- Lounge fonts, original Stage SVG paths, original locked branding, current Android sports backgrounds and 1920×1080 layout.
- Customer gateway login, current catalogue and canonical category IDs/order, movie and series metadata, seasons/episodes, short EPG.
- Major Events carousel, current sports calendar, existing backend watch candidates and confidence labels. The client does not change matching or promote candidates to playback-verified status.
- Home shelves, exact catalogue matching for Trending and the original Android editorial title seeds.
- Live browsing, programme pane, schedule and guide; Movies/Series virtual grids; Saved; Search; relevant settings.
- D-pad navigation, hold OK menus, bounded list rendering, stable media identity and return focus.
- One native HTML video surface, seek/resume, current episode progression, native-exposed audio/subtitle selection, bounded recovery and local redacted diagnostics.
- Account/profile-scoped local favourites, progress, team follows and reminders. Reminders display while the app runs.

## Isolation

Only the LG project is changed. No Android source/signing, provider data, customer accounts, database, functions, gateway, catalogue, EPG pipeline or sports algorithm is changed or deployed.

## Acceptance

Use [DEVICE-QA.md](docs/DEVICE-QA.md). Unit and browser checks do not replace the LG tests. Remaining parity differences and security/storage decisions are explicit in [PARITY.md](docs/PARITY.md).

The original Lounge/OwnTV source and copied Stage assets are covered by GPLv3. The bundled Plus Jakarta Sans font retains its upstream licence in `docs/FONT-LICENSE.txt`. Assets are copied, not regenerated.
