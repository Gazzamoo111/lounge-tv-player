# QA 0.1.2: Live playback continuity

## Evidence and scope

The user confirmed 0.1.1 was much faster, but five Live channels stopped after approximately 2–3 minutes. Without a physical LG trace the trigger is unproven. Source review identified deterministic recovery defects: native Live EOF was treated as terminal, unexpected pauses were left paused, the two-attempt recovery budget never reset, and transport-clock resets could be mistaken for a stall.

The native fullscreen video was also inside a transformed viewport. LG's official FAQ says fullscreen video must occupy 100% of the viewport at 0,0 without transforms/opacity that interfere with recognition, or the screensaver may launch. 0.1.2 removes fullscreen ancestor transforms and uses the viewport bounds. This may explain the similar interval across channels but requires device confirmation. The app does not disable the screensaver.

References: https://webostv.developer.lge.com/faq and https://webostv.developer.lge.com/develop/guides/screensaver .

## Changes

Live EOF and genuine stalls reconnect the same canonical gateway route, releasing the old native connection before replacement. Foreground unexpected pauses first resume the existing connection. Four consecutive retries back off 1/2/4/8 seconds; 30 seconds of sustained progress restores the budget. Explicit pause and backgrounding remain respected. Progress accepts backwards clock changes and decoded-frame progress, and is polled independently of timeupdate events.

A bounded 180-entry redacted trace records native events, elapsed/transport timing, ready/network states, buffer ahead, frames and recovery attempts. It contains no stream URLs or credentials and is saved locally before app background/page exit. Actual decoding is not inferred from a successful play promise.

Only one Live parent remains expanded. Fullscreen Live Left/Left opens channel/category drawers, Back reverses those levels. The drawers leave the video surface untouched; selecting a category does not retune until OK selects a channel.

## Validation

- 31 domain/native-player tests passed, including repeated EOF at simulated 150-second intervals for more than 20 minutes of virtual time, pause intent, transport-clock wrap, missing timeupdate, decoded-frame progress, bounded recovery, cancellation and VOD end behaviour. Virtual time is not actual provider playback.
- 19 browser journeys passed at 1920×1080, including fullscreen native geometry, no transformed ancestors, Left/Left/Back/Back without pause/reload, category browsing without retuning, single-open headings, native EOF recovery and redacted persisted diagnostics. Media events are synthetic; no actual LG decoding is claimed.
- TypeScript build and native IPK archive/identity/assets checks pass.

## Remaining acceptance

Run the priority device gate in DEVICE-QA.md: at least 10 minutes untouched on a previously failing channel, then repeat across the other reported channels. Obtain the retained native trace if failure recurs. Sustained LG playback, codec coverage and the precise original trigger remain unverified.

Sport Lounge coverage is explicitly deferred. Android, customer accounts, provider mappings, canonical catalogue, gateway, EPG pipelines and sports matching are unchanged.
