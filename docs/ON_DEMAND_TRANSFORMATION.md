# Lounge TV On Demand | Midnight Electric

> **QA-only design and implementation contract** · `feature/lounge-tv-on-demand` · PR #79  
> **Identity:** `com.loungetv.player.ondemandqa`  
> **Status:** Phase 1 implementation in progress. Not production-ready and not approved for distribution.

## Design decision

Lounge TV On Demand is an **artwork-first entertainment library**, not a channel browser and not an alternative skin for Lounge TV Live. Reference Nuvio's efficient TV navigation and confident poster presentation, but do not copy artwork, icons, source code, trademarks, or proprietary UX assets.

**Visual direction:** *Midnight Electric*. A deep, quiet midnight-navy canvas lets high-resolution film imagery do the work. Electric lime is reserved for active selection, primary actions, completion and playback focus. Slate surfaces organise supporting information. Typography is concise, large enough to read from a sofa and consistent across the application. The overall impression should come from layout, motion and visual clarity rather than decorative gradients.

| Token | Value | Use |
| --- | --- | --- |
| Midnight navy | `#081528` | Page background, cinematic artwork wash |
| Electric lime | `#B7FF35` | Selected navigation, focus ring, Play, active indicators |
| Slate blue | `#182840` | Elevated surfaces, secondary panels, controls |
| Cool white | `#F5F8FC` | Titles and high-priority text |
| Muted slate | `#B3C2D6` | Synopsis, supporting metadata |

The scope must be branch- and build-conditional on `BuildConfig.LOUNGE_ON_DEMAND`; all shared Live UI paths retain their previous behaviour.

## UX structure and screen inventory

| Surface | Required experience | Current QA work | Remaining work |
| --- | --- | --- | --- |
| System splash & launcher | Dedicated On Demand art, unique app mark, no Live TV launch identity | Separate APK/package/label exist | Original Android splash/icon resources still present; replace with a distinct QA-safe launcher and splash without modifying Live |
| First-run welcome | Bold brand, immediate setup choice | New branded welcome composable | QA test focus and persistence across failed/cancelled setup |
| Authentication/setup | Phone/PIN transfer as primary, secure manual fallback, go Home when import succeeds | Existing PIN-protected LAN phone transfer now reachable immediately; manual entry reachable directly; profile created automatically | Phone transfer still pre-fills the form rather than validating a cloud account; streamline safe submit/import, add secure backend device-code pairing only after backend designed |
| Top navigation | Home · Movies · Series · Search · My Lounge · Downloads · More | Dedicated horizontal menu conditional on QA build | Hardware D-pad focus/Back restoration, TV safe-area and content overlap QA |
| Home feature | Actual catalogue title, cinematic backdrop/logo, concise metadata, two actions | Reskinned and crossfaded hero; catalogue actions retained | Evaluate hero-to-rows transitions and media-title cropping on Fire TV |
| Home shelves | Curated content, Continue Watching, releases, films, series | Existing catalogue-backed rails; poster radius/type refinements | Consolidate spacing, focus lift, highlighted title backgrounds and shelf ordering |
| Movie browser/details | Large poster, contextual backdrop, prominent title/Play, concise metadata and description | Existing VOD browsing preserved | Distinct On Demand details layout rather than reused Live sidebar/card |
| Series browser/details | Season navigation, episode stills, watch progress, easy next episode | Existing functionality preserved | New focused episode/season treatment and navigation QA |
| Search | Single decisive search, clear focus, artwork-backed results | Existing VOD-limited search preserved | Redesign result states, keyboard focus, zero-result content and voice search only if supported |
| My Lounge/watchlist | Movie/series lists, continue history, no live content | Existing VOD-only filtering | Refine empty, partially watched and completed states |
| Settings | Playback, language/subtitles, appearance, support; no EPG/live complexity | Existing settings reused | Hide/retire Live-only settings at QA build level; simplify labels/grouping |
| Playback stream badges | Decoded source resolution/HDR/audio only, no filename claims | First decoder-only chip model + tests; shown on active stream information overlay | Complete player-HUD placement, HDR10+/DV and codec metadata evidence from engine; verify ExoPlayer/MPV parity |
| Loading/error states | Graceful artwork fallbacks, consistent navy surfaces, retry actions | Existing functional states, QA palette | Dedicated loading skeleton, offline and setup-failure screens |
| Future add-ons | Optional multi-provider source catalogue, user-owned accounts | Design only | Phase 2 after licensing, provider terms, privacy, consent and secure authentication |

## TV remote and focus rules

- A focused item is unambiguous through **one lime focus state** and coherent movement/scale. Do not use a second accent merely for decoration.
- Navigation is horizontal. Down enters current content and Back exits nested screens before returning to Home. D-pad must never lose focus on scroll or artwork refresh.
- Enter/OK starts **Play** only when Play is deliberately selected. Poster focus and synopsis preview must not unexpectedly start playback.
- Preserve the current MPV/ExoPlayer fallback, watch-position persistence, playlist association and provider catalogue models during Phase 1.
- Respect animation-off settings, low-memory Fire TV limits and remote control repeat speed; avoid decode-heavy autoplay backgrounds.

## Badge truth model

Badge levels, in priority order:

1. **Verified playing stream:** active decoder/video format, selected audio track, and actual HDR signal. Display technical badges such as `1080p`, `4K`, `HDR PQ`, and `5.1 source` only with evidence. Mark `Atmos stream` only for direct selected JOC evidence, never as a promise of TV/soundbar output.
2. **Provider-advertised / inferred:** filenames, catalogue quality rank and provider labels are *not* playback facts. Do not put them in the premium verified badges.
3. **Not known:** show nothing. In particular PQ ≠ HDR10+, 10-bit ≠ Dolby Vision, and a provider's DTS-HD label does not prove the selected track.

Nuvio Mobile PR #2414 is an implementation reference for negative cases: distinct evidence groups, codecs, selected-track metadata, audio-profile false positives and avoiding misreads such as `German` becoming DTS-HD MA. It is **not copied code**, and its filename enrichment policy is not treated as playback proof.

Current typed core stream info can verify active resolution, generic HDR PQ/HLG and codecs/channel counts, but does not establish HDR10+, Dolby Vision profile or full Atmos/DTS:X output path. These badges remain unavailable until accurate engine evidence exists.

## Future add-on architecture (design only, not implemented)

```text
On Demand UI (home, detail, source chooser, playback)
    |
Unified media identity + entitlement/rights check
    |
Source discovery aggregator
    |-- BEST VOD catalogue adapter (existing; default, unchanged)
    |-- Stremio-compatible add-on adapter (future, opt-in)
    |     |-- AIOStreams-compatible manifest/source discovery
    |     `-- user-configured eligible add-on sources
    `-- Additional authorised sources (future)
    |
Source records: media match, provider, quality, language, codecs, origin, rights
    |
Explicit user source selection + compatibility checks
    |
Playback coordinator (existing ExoPlayer / MPV with fallback)
    |
Verified decoded-stream badge reporter
```

Security: avoid exposing account cookies, Real-Debrid API tokens, BEST credentials or provider secrets to third-party add-ons or embedding shared secrets in the APK. Store per-user credentials in protected storage; use the minimum scope, expiry/revocation and per-provider consent. If a service requires remote token exchange, use a controlled backend and short-lived device-code authorization. Respect service terms and distribution/streaming rights; reject unlicensed or unauthorised sources.

Failure isolation: an add-on timeout must never block the BEST-first catalogue, app Home or player; isolate add-on requests, cache response cautiously, disclose source provenance and make every integration disableable. Keep add-on identifiers separate from BEST content IDs; match by stable metadata rather than mutating original data.

## Phase gates

**Phase 1 is complete only when:** all screen families share the intended visual language, dedicated launcher/splash and onboarding are finished, real Fire TV navigation/focus works without regressions, all supported technical badge claims are verified, and GitHub QA compiles and produces a signed, separately installable APK with unchanged playback/history.

**Phase 2:** source and add-on integration, Real-Debrid (user-controlled and rights-compliant), expanded codec evidence, advanced stream-source chooser and deeper player tuning. Phase 2 is out of scope for the initial visual QA.

**Release restrictions:** Do not modify Live or production, do not merge PR #79, do not distribute a customer update, and do not claim Fire TV acceptance from a successful CI build alone.
