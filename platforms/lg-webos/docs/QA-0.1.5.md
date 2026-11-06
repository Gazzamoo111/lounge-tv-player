# QA 0.1.5: Remote movement, continuous Guide and NZ playback

## Findings and changes

The Guide selected directional targets by screen coordinates and programme centres. Different programme lengths could send Down to a different channel row. It also split channels into manual pages. `GuideGrid` now virtualises the entire selected category and moves exactly one channel on Up/Down, keeping an intended programme time. Its DOM remains bounded while reaching the final channel. Horizontal movement traverses programme cells, then advances the time window; Left reaches the channel label and sidebar. Now/Earlier/Later preserve the selected channel.

Delayed programme responses and minute refreshes previously replaced a focused button. New cells restore that row/time focus without rebuilding the shell. The focus controller also recovers the previous visible scoped target if browser focus is lost. Focused guide reads are promoted in a two-request queue; scrolled-out stale work is discarded. These address identified client causes of idle sluggishness. Actual TV memory/CPU behaviour still requires acceptance.

The Home hero previews an event as its selector receives focus, without Enter. Auto-rotation pauses while the remote is in the hero. All hero event crests/logos are removed; individual-sport event cards use their title instead of competitor icons. Explicit paths connect hero actions/selectors, shelves, categories and virtual grid boundaries. Unsupported golf/tennis artwork uses the neutral hero background instead of unrelated combat imagery. The clock/date occupies a smaller fixed top band; long status labels truncate within their buttons.

## NZ source repair

Read-only source inspection found reserved Freeview stream IDs routed through the shared gateway's generic BEST numbered-stream path. Android's published v5.2.3.10 has separate direct NZ sources. The LG client now ports those source choices locally, without changing provider bindings, database rows or the shared gateway.

- TVNZ 1/2: direct MJH DASH manifests and normal Widevine license endpoints, loaded using bundled Shaka Player 4.16.12 and the TV's EME implementation.
- Three, Whakaata Māori, Te Reo: the audited direct HLS endpoints through the existing native video path.
- Ambiguous names such as Three only match exact NZ guide identity. TVNZ names may match their unique brand. VOD and ordinary provider channels retain their existing routing.

Shaka is lazy-loaded for TVNZ only. Its license is included in the IPK. Adaptive sessions are destroyed before the next native session starts. Cancelled asynchronous factories cannot attach to a newer stream. The watchdog waits for adaptive preparation before trying to resume a paused surface, while retaining the startup deadline. Errors retain numeric codes, without manifest/license URLs or credentials in diagnostics.

Public cloud HTTP probes returned HLS manifests for Three/Māori/Te Reo. TVNZ manifests returned HTTP 403 from this cloud environment, so successful manifest delivery, license acceptance, first decoded frame and audio on the New Zealand LG remain unverified. Browser routing/lifecycle tests use mocks and do not establish DRM or real stream success. The browser cannot supply Android's custom User-Agent. If TVNZ is still blocked on the TV, its on-device error is needed to distinguish network/source denial from DRM/device failure.

## Validation

54 domain/native/adaptive lifecycle tests and 37 browser journeys pass. Checks include all existing startup, EPG, VOD, Settings, fullscreen drawer and recovery regressions. Added cases reach the final row in a 2,391-channel Guide, traverse unequal-length programme rows without skipping, retain focus across delayed EPG responses and an idle minute, recover lost browser focus, preview hero events on directional focus, pause rotation during hero browsing, constrain clock/date with long status names across six pages, and verify the TVNZ DASH/license/native-HLS session handoff. A delayed adaptive preparation test ensures the watchdog does not prematurely restart it.

TypeScript compilation, real `ares-package` creation, and IPK unpack/identity/vendor-asset checks pass. Browser screenshots inspected at 1920×1080. These checks do not replace physical LG testing. See DEVICE-QA.md for the concise device sequence.

## Scope

Only the isolated LG webOS QA client changes. Android, production deployments, customer category membership/order, sports matching and source databases are unchanged. Full Android feature parity is still incomplete. Physical acceptance of this 0.1.5 build is outstanding.
