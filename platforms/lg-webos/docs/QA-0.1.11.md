# LG QA 0.1.11

The 0.1.10 hero test injected logo URLs and missed the production calendar's missing data. Read-only live calendar inspection found 11 events: four rugby league fixtures and seven combat events. All crest URLs were null.

A legacy CSS override also hid the entire hero team panel. Remove that override and assert visible images, not just DOM element counts.

Bundle 15 official RLWC nation marks from the official RLWC assets host, including distinct women's New Zealand, Australia and Fiji artwork. Resolve these locally for rugby league events only. Other team fixtures use provider crest URLs. The hero and sports cards share this resolver. Generic individual-event artwork remains suppressed.

Preserve valid discovered team artwork when duplicate calendar fixtures contain null URLs, matching competitors by ID or normalised name regardless of array order. Accept ESPN logo arrays and single-logo fields.

Verification: unit regressions using actual null-URL calendar fixtures, reversed competitor merge and scoreboard variants; browser regression checks actual bundled image decoding, not just image elements. Package verification requires every bundled crest. No playback, license, catalogue or backend changes.
