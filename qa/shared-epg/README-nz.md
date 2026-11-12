# NZ shared-guide refresh (QA)

The shared guide had NZ programmes ending 7 November 2026 because the scheduled AU/24-7 workflow does not refresh NZ. This change adds a separate NZ refresh using the existing Lounge TV release IDs.

* MJH NZ Freeview XMLTV is the required source for TVNZ 1/2, Three, Whakaata Māori and Te Reo. These must have current, exact-ID schedules or publication is blocked.
* EPGShare NZ1 is an optional supplementary source for NZ Sky Sport 1–9. Exact IDs are preferred. Only unique NZ-only broadcaster names may be used as fallbacks. No fuzzy matching, invented listings or synthetic seven-day schedules.
* Only expired (older than 48 hours) programmes for verified NZ targets may be removed. All current and future programme intervals are preserved.
* Short guides are regenerated only for changed NZ IDs. The report counts matched channels, source IDs and actual seven-day horizons.
* QA runs unit tests, fetches the existing published guide, builds a candidate and enforces five working Freeview schedules. PR runs never publish.
* When later merged into main, scheduled validation runs at 03:33 and 15:33 UTC. Publication checks the original epg-data SHA to prevent overwriting concurrent regional updates.
* No playback, channel catalogue, production gateway or client changes are included. Source licensing and QA need acceptance before release.

Test locally by installing lxml==5.4.0 and running: python -m unittest test_repair_nz test_repair_missing -v

This is a focused NZ repair, not a claim that 95% of the entire catalogue now has genuine seven-day programme coverage.
