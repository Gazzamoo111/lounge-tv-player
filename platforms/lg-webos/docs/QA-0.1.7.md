# QA 0.1.7: Splash, phone sign-in and early playback failures

## User findings

The supplied LG photo shows `fullscreen` and `load` records in `idle`, with attempt zero. It does contain trace entries, but no failed-attempt diagnosis. This narrows the investigation to failure or waiting before the usual first attempt; it does not prove a manifest, DRM or broadcaster fault. Earlier claims that the photo would necessarily expose a TVNZ code were too broad.

## Startup and diagnostics

A catch boundary now covers the complete attempt startup, including waiting for previous adaptive teardown and native listener/state setup. Startup-entry and teardown-wait milestones are recorded before the attempt counter advances. An early exception becomes visible code 9003, with safe exception class and bundled-script line/column when supplied by the engine. Arbitrary exception text, URLs, stack contents, credentials and license data are not retained. An unresolved teardown stops after eight seconds with code 9010; a second player is not attached to a surface still being destroyed.

Unhandled promise failures now enter the retained trace. The Error log puts its latest issue summary at the top and lists the most recent thirty records in reverse order. The previous UI began with the oldest trace records, which made recent failures difficult to find in a photo. This is an observability/startup-boundary repair, not proof that TVNZ 1/2 now decode. Actual TVNZ picture/audio remains an open LG acceptance test.

## Splash and login

The packaged HTML shows the existing Lounge TV couch mark and wordmark immediately, before the application bundle runs. The in-app splash stays briefly visible, then existing saved credentials proceed directly to the unchanged membership/playlist/EPG preparation screen. A first-time or changed login opens a clear two-column choice: phone QR or username/password on the remote. Normal remember-login behaviour remains.

The QR flow is a bundled local LG JavaScript companion service, not a cloud pairing account. The TV generates a random 256-bit token valid for ten minutes and a same-network phone URL. The code contains no username or password. The phone page is served by the television, removes the token from its visible URL/history, and submits credentials to that local service. Only the LG app may start, collect or cancel via Luna. The service holds credentials only in memory; collection is one use and closes the listener. Refresh invalidates the old session. Cross-origin submissions, wrong tokens, missing fields, repeated submissions and excessive bodies are rejected. The app polls only while the phone sign-in screen is active, keeping the service responsive, and cancels on manual sign-in, session start, refresh or page exit.

Both devices must be on the same network, with local device access allowed. This is the same type of local companion flow used in the Android reference. A normal remote login remains available if the service or network is unavailable. No public database, gateway, website or Supabase deployment changes are involved.

## Validation

62 domain/native/service tests pass. These include real local HTTP requests against the companion, nonce rejection, cross-origin rejection, credential validation, one-use collection, refresh/cancellation, pre-attempt exceptions and stuck adaptive teardown. The browser suite has 41 journeys, including splash visibility and a real local phone-form submission collected through a simulated Luna bridge, followed by the existing mocked membership/catalogue/EPG startup. The phone HTTP service and form are real in that journey; the physical LG Luna bridge and provider account are not.

Native IPK creation and unpacking check the web app, all twelve hero images, the QR dependency notice, and the complete companion service with its metadata. The service uses CommonJS and older-compatible Node syntax for TV portability. Original native playback, guide and VOD regressions remain covered. No claim of successful physical LG QR login or TVNZ playback is made before device acceptance.

## Device acceptance

1. Install over 0.1.6 without uninstalling. Relaunch and check the logo splash, then the existing preparation screen and saved login.
2. To test a new login, use Settings → Playlists → Change playlist login. Scan the QR on a phone connected to the same Wi-Fi. Enter normal Lounge TV credentials on the phone. The TV should continue automatically into membership/playlist/EPG preparation and then Home.
3. Check manual remote login, QR refresh, a wrong credential attempt and relaunch persistence. Do not send a real credential screenshot.
4. Try TVNZ 1/2. If either still fails, Error log now begins with the latest issue summary and newest records. Capture that summary. Code 9003 identifies early startup failure; 9010 identifies unresolved player teardown. Manifest/license codes retain their actual numeric/HTTP details when reached.
5. Retain smooth ordinary Live playback, Guide scrolling/idle focus, the NZ HD checks and VOD resume.
