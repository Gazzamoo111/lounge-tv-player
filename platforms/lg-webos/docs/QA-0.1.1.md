# QA 0.1.1: startup and navigation

## Trigger and expected change

0.1.0 opened Home immediately, then repeatedly rebuilt the shell as catalogue, sports and metadata requests completed. The LG user reported extremely slow navigation, an expanding sidebar, missing hierarchy, overlap and inability to re-enter through the launcher icon.

0.1.1 gates Home behind an explicit preparation screen, persists the complete received catalogue and full XMLTV guide, reuses a stable icons-only shell and renders Live categories as collapsible parents and children. List windows retain existing cards as the user moves. The backend's complete category list is retained, including empty folders, without changing provider membership or ordering.

## Validation

- 23 domain/native-player lifecycle tests passed.
- 15 browser journeys passed at 1920×1080 with fixtures containing 2,391 live streams, 5,142 movies and 3,395 series.
- The delayed-library test proves no main interface is exposed until all catalogue libraries are ready. The full-XMLTV fixture is parsed off the UI thread, committed and reused on restart. The restart check proves zero new full-catalogue or XMLTV requests within the fresh-cache window, while membership is still rechecked.
- Sidebar width remains 128 px on focus and navigation. Its DOM node survives route changes. Profiles has a distinct accessible label; one actual Home destination remains.
- Category tests cover independent collapse/expand and preservation of all supplied IDs, including empty and interleaved folders.
- Virtualised browsing retains overlapping poster nodes and a bounded DOM. Long-list Back/return, Saved, series routes and profile isolation regressions pass.
- `ares-package` produces a real IPK. Archive structure, identity/version, worker, assets, required entrypoints and no bundled QA/provider credentials are checked.
- The app now sets `handlesRelaunch: false`. LG documents that this makes webOS bring the app to the foreground automatically. 0.1.0 incorrectly claimed custom relaunch handling without the required activation handler. References: https://webostv.developer.lge.com/develop/references/appinfo-json and https://webostv.developer.lge.com/develop/guides/app-lifecycle-management .

## Device gate and remaining work

These checks prove browser/client behaviour, not LG performance. User installation and device testing of **0.1.1** remain required. Do not advertise a measured TV speedup or working codec/stream based on the browser tests.

First launch needs fresh data. Later launches reuse a complete cache for six hours. Settings → Refresh catalogue and programme guide forces a new preparation. Empty or failed EPG sources remain visible as missing, never fabricated. Whole-guide failure falls back to available per-channel guides with an explicit notice.

QR authentication, complete settings parity, full Android Home/discovery parity and failing-stream/codec investigation remain outside this repair. No ExoPlayer/MPV equivalent is claimed. Android source/signing, customer accounts, canonical backend catalogue, provider settings, gateway, EPG pipelines and sports matching are unchanged.
