# Repair after QA #678 physical feedback

QA #678 was rejected in physical use. Its automated checks did not establish Home-to-Sports full-screen routing or persistent real video output through preview/full-screen transitions. This repair follows the reported behaviour, not a title-specific engine/buffer workaround.

## Confirmed causes and changes

- Home event discovery can produce backend candidate channels with synthetic negative IDs. Their preview uses the channel object, but full-screen routing looked up that ID in Room and returned without opening the player. The validated sports object now follows the existing canonical tune path with profile/source/adult visibility checks and the candidate CH context. Home's explicit watch actions also force full-screen instead of preserving stale mini-player state.
- Preview and full-screen previously created different Exo SurfaceViews. The connected Fire TV's diagnostics show `video surface detached` at 13:32:20, continuing audio/position with a frozen rendered-frame count, a false frozen-picture reconnect at 13:32:29, and no picture after the reconnect. A movable shared video view now follows the existing live session between preview and full-screen. The frame watchdog ignores an intentionally absent output and restarts its observation window on real surface changes; audio/position health checks remain active.
- The MPV/Exo-VOD surface callback now calls the existing identity-safe `detachSurface(surface)` overload. Destruction of an old view cannot detach the new view's output.
- Sports match/feed state remains mounted but inactive beneath live full-screen. Hidden preview/search/focus effects are gated, background controls cannot receive focus, and pending match requests are consumed when the screen becomes active again. VOD full-screen retains the previous browse disposal behaviour.
- Synopsis Back previously hid the bar on KeyDown, then let the same press's KeyUp reach the shell and leave playback. The persistent HUD now owns the complete down/repeat/up gesture; a later separate Back can still leave the player.
- QA #667 top-level category icons are restored. Provider and custom subcategory rows are text-only. The rejected QA #678 glyph additions are removed.
- Home movie posters use the same rating, progress and watched badge state/formatter as VOD posters. Continuation ratings use one bounded lookup; unknown values do not become invented badges.

## Playback scope and limits

The owner subsequently confirmed Harry Potter was playing again. Its stream, engine pin and settings were not changed. Ocean's Eleven's continuing source/codec/lip-sync behaviour has not been independently established as fixed; no title-specific adjustment or generic buffer/audio-offset change is made or claimed. The repair addresses the confirmed shared routing, lifecycle and surface-ownership faults. The currently playing Fire TV session was left alone.

The actual device has auto frame rate disabled, so no AFR preference change was needed. Production, backend routing, XMLTV sources and default VOD-engine policy remain unchanged.

## Verification

The existing automatic gates remain required before delivering the replacement signed ARM APK. Focused coverage includes the complete physical Back gesture at the HUD/shell boundary, negative-ID sports preview promotion with one engine open, rejected profile/source routes, Home/VOD badge semantics and a generic local-video surface handoff check that verifies the same view/surface and actual non-black pixels through both directions. There are no provider-title playback tests or remote UI actions interrupting the owner's current film.

Keep PR #68 draft. Final replacement QA number, signing identity and build evidence are recorded in the delivery report after CI passes. Do not represent automated local-media checks as physical acceptance or claim every movie/source issue is resolved.
