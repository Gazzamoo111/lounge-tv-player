# Sport Lounge fixture coverage audit

Audit date: 2026-10-26 (Pacific/Auckland). This report describes fixture data only. It does not assert that any BEST channel carries an event or that playback has been verified.

## Runtime sources

The Android app currently uses the existing published OwnTV Core ESPN adapter. Sport Lounge adds an exact-ID provider-neutral model, a bounded cache and a parser for the central response contract. A Cloudflare Worker implementation is included under `services/sport-lounge/`, but it is intentionally not deployed or enabled in the app. Deploying infrastructure was outside this branch’s authority.

| Competition | Verified result | V1 status |
|---|---|---|
| NFL | ESPN returned 1 event for 2026-10-26 and 60 events for October 2026. The event date is stored in UTC and appears on the correct NZ local day. | Supported by direct discovery and gateway code. |
| Rugby League World Cup (men/women) | The official NRL and RFL pages publish 2026 fixtures, but the existing ESPN `rugby-league/3` feed is NRL-only: 0 events on 2026-10-26 and only the NRL grand final in the October month response. | Unsupported in runtime until a permitted machine-readable provider is approved. No official-page scraping is implemented. |
| Rugby internationals | ESPN `rugby/289234` returned 3 October internationals (New Zealand–Australia twice and Japan–Fiji), but 0 on 2026-10-26. | Supported, incomplete outside ESPN’s international-test feed. |
| Cricket | ESPN’s cricket header returned dated event objects for 2026-10-26. A schema verification on 2026-10-26 included stable series/event IDs, UTC dates, competitors, status and scores (for example Ranji Trophy event `1546580`). | Supported directly by OwnTV Core and by the gateway's separate cricket-header parser. This is broad ESPN cricket discovery, not a promise that every domestic competition is complete. |
| Australian NBL / NZ Breakers | ESPN `basketball/nbl` returned 34 October fixtures, 6 involving the New Zealand Breakers. The alias `basketball/australian-nbl` returned HTTP 400. | Supported using the verified `basketball/nbl` slug. |
| Netball | The tested ESPN `netball/netball` endpoint returned HTTP 400, and no permitted production feed is configured. | Unsupported. Requires an approved provider/API agreement. |
| NBA | ESPN returned 9 events for 2026-10-26 and 155 for October. | Supported. |
| Major football | Existing OwnTV Core catalogue covers selected FIFA, UEFA and domestic competitions. The bounded default discovery set now includes Australian A-League Men, English Premier League and UEFA Champions League. Coverage remains league-by-league rather than universal. | Supported for the three gateway/default-discovery competitions plus other user-followed catalogue leagues. |
| Formula 1 | ESPN’s October/November range exposed 7 sessions/events, including the upcoming Mexico City Grand Prix. | Supported. |
| UFC | ESPN’s October/November range exposed 10 UFC/Contender Series events. | Supported. |

## Provider and licensing limits

- ESPN’s public endpoints require no app credential, but provide no contractual completeness guarantee. The existing client backs off on HTTP 403/429; Sport Lounge retains stale cached rows only for the exact feed that failed and displays the last-successful timestamp.
- The draft gateway enforces the same boundary centrally: 403/429 pauses only the exact provider feed (honouring `Retry-After`, otherwise 5–60 minute exponential backoff), valid cached rows are retained only for that failed feed and requested UTC window, and diagnostics report `ok`, `partial`, `stale` or `unsupported`. A stale row keeps its original `lastSuccessfulUpdateMs`.
- [TheSportsDB documentation](https://api.thesportsdb.com/documentation) advertises a free key and a 30 requests/minute limit. Its [terms](https://www.thesportsdb.com/docs_terms_of_use.php) state that free API data cannot be used in an app-store publication. It is therefore not enabled. A paid key and explicit approval would be required.
- Official Rugby League World Cup fixtures are visible at [NRL](https://www.nationalrugbyleague.com.au/world-cup/) and [RFL](https://www.rugby-league.com/world-cup), but no page scraping is performed.

## Known blockers

1. Netball and Rugby League World Cup need a permitted production API with stable IDs and redistribution terms.
2. The central Worker remains undeployed; Android continues to use the existing source plus bounded device cache. Its code and response parser are ready for a separately approved endpoint rollout.
3. Fixture presence is not stream availability. BEST matching and actual playback verification remain separate.

## Gateway contract and enabled feeds

`GET /v1/sports/events?from=<ISO-or-epoch>&to=<ISO-or-epoch>&sport=<optional>` accepts windows up to 72 hours. Event identity is `espn:<feed>#<provider-event-id>`; updates deduplicate only on that exact identity. Terminal status cannot regress to live/scheduled because an older or out-of-order payload arrived. The response deliberately contains no `watchable`, channel or playback field.

Enabled no-secret feeds: NFL; NRL (`rugby-league/3`, not Rugby League World Cup); international rugby (`rugby/289234`); ESPN cricket header; Australian NBL; NBA; Australian A-League Men; English Premier League; UEFA Champions League; Formula 1; UFC. Netball and Rugby League World Cup are accepted as known coverage requests but return an honest `unsupported`/empty result rather than synthetic fixtures.
