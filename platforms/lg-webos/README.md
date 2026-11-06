# Lounge TV for LG webOS

Isolated LG client, QA package `nz.loungetv.webos.qa`, version **0.1.4**. Full Android feature parity is incomplete. The user confirmed substantially faster startup/navigation in 0.1.1 and 15 minutes of Live playback without the former 2–3 minute stop in 0.1.2. 0.1.3 addresses VOD responsiveness, All-tab focus, EPG row loading/matching, Settings structure and sports discovery. Physical LG acceptance of the new changes remains required.

Android reference: published **v5.2.3.10**, commit `44388fa8683df20a51ad5166f3ea73d7b08c8051`. Current `main` is older in application features, despite having newer documentation and EPG changes. See [parity matrix](docs/PARITY.md).

## Install the provided package

With the `.ipk` downloaded to the Mac's Downloads folder and the existing `loungetv-lg` device configuration:

```bash
cd ~/Downloads
ares-install --device loungetv-lg nz.loungetv.webos.qa_0.1.4_all.ipk
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

Output: `artifacts/nz.loungetv.webos.qa_0.1.4_all.ipk`.

Browser regressions use Chromium and mock the real API contracts with a large catalogue. They do not establish actual provider playback or customer authentication:

```bash
npx playwright install chromium
npm run build
npm run test:browser
```

`PLAYWRIGHT_CHROMIUM_EXECUTABLE` can point to an existing Chromium executable. `npm run serve` serves the built application at port 4173.

## 0.1.4: EPG recovery

- Adds automatic, region-restricted NZ and Australian (Sydney) guide fallbacks, fetched directly from the existing public regional guide repository. Current canonical entries retain priority; supplemental entries fill real gaps. First launch refreshes the guide cache automatically.
- Separates cached guide data by stream identity. Reviewed regional aliases resolve Australian FTA names; Ten no longer inherits 10 Bold's schedule from their duplicated upstream guide ID.
- Future-only cached entries no longer suppress a request for the programme on now. Short-guide fallback merges current and future entries; explicit manual mappings do not silently fall back to the previous guide identity.
- Preserves a previous valid guide generation if some feeds fail. A partial refresh cannot replace every unrelated channel's guide with blanks. Failed sources become eligible for retry after 15 minutes on the next startup.
- Settings reports current-entry counts and per-source health. Visible Live rows update quietly each minute. Guide parsing skips unneeded channels before decoding full programme descriptions.
- Upstream UK/US, PPV and rotating-channel gaps remain. This does not invent listings or change channel membership, source bindings, playback routing or the shared production API.

See [QA-0.1.4.md](docs/QA-0.1.4.md) for real-feed audit evidence and limitations.

## 0.1.3: responsive libraries, guide, Settings and sports

- Movies/Series were already fully downloaded before Home. Opening artwork is now warmed within a bounded startup budget; poster/backdrop decoding is sized for TV, metadata/backdrop browsing work defaults off, remote navigation uses a small DOM window and profile Saved/progress lookups use indexes. Movie Play/Save appears immediately from the prepared catalogue. A long episode season is virtualised rather than creating every episode at once.
- Entering Movies/Series from the sidebar starts on All movies/All series, with Search unfocused. Details and playback still restore their previous grid position.
- Visible Live rows now request missing guides with two concurrent reads. Full-guide matching accepts supplied IDs, canonical stream aliases and unambiguous exact display names; manual EPG matches, extra HTTPS XMLTV sources, source controls, time shift and refresh are available. The canonical gateway already serves Lounge XMLTV: it is not added twice.
- Settings follows the twelve Android groups, with an imported catalogue of 110 source rows, Search, context help and Quick pinning. Playlist switching/addition, guide configuration, profiles, local backup, appearance/layout and exposed playback preferences work where implemented. Unported and Android engine features are clearly unavailable. This is not full working Settings parity.
- Sport Lounge adds Android's 31 fixture feeds with four concurrent requests, seven NZ calendar dates, nested tennis/racing/MMA parsing, safe fixture merging and progressive population. Seasonal empty feeds stay empty; matching algorithms and customer channel membership are unchanged.
- Duplicate blank-player resets are removed from channel changes. First decoded-frame timing is logged where the TV exposes it. No measured LG startup-speed improvement is claimed yet.

See [QA-0.1.3.md](docs/QA-0.1.3.md). `scripts/import-settings.py` regenerates the reference catalogue with explicit `--android-root` and `--core-root` checkouts; it is not required for building the app.

## 0.1.2: Live playback and fullscreen navigation

- Native fullscreen video occupies the viewport at 0,0 without transformed ancestors. LG warns that transforms can prevent fullscreen recognition and allow the screensaver to run. This is a plausible contributor, not a proven diagnosis of the reported stop.
- Live EOF reconnects instead of ending the viewing session. Unexpected foreground pauses resume the existing connection; intentional pauses remain respected. Recovery releases the old connection first, permits four consecutive retries, and resets that budget after 30 seconds of healthy progress.
- Progress detection tolerates backwards transport-clock changes and samples decoded frame counts where available. Missing timeupdate events alone do not trigger a restart.
- Redacted native event, timing, buffer and retry diagnostics persist locally across relaunch. Playback errors expose a Playback details button.
- Only one Live parent heading is expanded. Fullscreen Live: Left opens channels, Left again opens categories. Back returns one level. Browsing never changes the stream until a different channel is selected.
- Sport Lounge coverage is deferred. No backend or Android changes.

See [QA-0.1.2.md](docs/QA-0.1.2.md) for evidence and the physical-device gate.

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
