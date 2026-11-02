# Movies and Series discovery QA

Baseline: production v5.2.3.2, commit 40603cebbcd4caa325f8dee0024c5a3605fca208.
This branch is a proposed UI update, not a production release.

## Result

- The six-across catalogue and its paging, search, category controls and long-press options remain.
- The first shelf uses existing matched Trending items in original feed order and draws their original ranks. Empty matches retain a clearly labelled Featured shelf without invented ranks.
- Opening a movie or series shows a backdrop-led title page, title artwork, concise facts, synopsis, Play/Episodes, Add to List, large cast photos and a selectable related-title shelf.
- Related titles use up to 40 visible candidates from existing category highlights. Genre matching uses only the local metadata cache in its configured language and maturity scope. With no shared cached genre, the shelf is explicitly labelled More from this category. With no valid matches, the shelf is omitted.
- Existing foreground metadata resolution remains debounced. The new page creates no trailer player, WebView or provider streaming connection.
- Recommendations are read on explicit title opening, not on every D-pad focus. No new server/API dependency, database migration or catalogue rewrite.
- Back returns to the original browser title; recommendation traversal stays inside the title page. Movies retain the existing resume prompt and playback path; Series retains its existing season/episode screen.

## Deliberate limits

- This is local genre/category discovery, not TMDB's recommendation endpoint, personalised recommendations or a claim that the full catalogue has been ranked.
- Cast names and photos use the current core schema; character roles are not available in that schema.
- No automatic trailers. Existing manual trailer controls elsewhere are unchanged.
- No APK ABI/compression or playback-engine changes in this proposal.
- UI appearance, focus and memory behaviour require physical-device verification. Passing JVM tests does not establish those outcomes.

## Required device checks

Use the separate PR QA package, not an update to production.

1. Open Movies and Series with metadata present, absent and disabled. Missing logos, backdrops, photos and genre data must produce usable fallbacks.
2. Verify original Trending ranks and correct Featured fallback. New Releases must never receive Trending numbers.
3. Navigate all six columns, the next row, categories, search, sort and Back. Confirm no clipped headings, posters or focus ring at the current Fire TV zoom/overscan setting.
4. Open a title, move between Play and Add to List, then reach the related shelf using Down. Ensure the cast section remains visible when traversing and does not trap focus.
5. Open multiple related titles, use Back and confirm the originating catalogue card regains focus. Repeat after scrolling well below the first page.
6. Start a movie with and without saved progress, including the resume prompt. Exit playback and verify the correct title and progress persist.
7. Open a Series title, choose Episodes and verify seasons, episode selection, resume and next-up behaviour.
8. Hide a title/category and switch profiles, including a kids profile. No hidden, foreign-language or other-profile source item may appear in Trending or recommendations.
9. Toggle Add to List and verify the existing Favourites view agrees. Long-press options, downloads and manual details must still work.
10. Browse normally, then play a known-good stream. Compare memory and responsiveness with production; the title page must leave no player or background metadata loop behind.

No overnight test or per-channel sweep is required for this UI proposal. Do not merge/publish until visual and D-pad checks pass on the test Fire TV.
