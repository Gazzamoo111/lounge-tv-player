# VOD discovery and full-library navigation QA

Branch: `feature/vod-discovery-navigation`, based on discovery QA `9a156de6`,
which includes the production 5.2.3.2 playback baseline. Production is unchanged.

## Architecture and behaviour

`LoungeVodBrowser` is the shared Movies/Series TV browser. Discovery is a lazy
vertical list of lazy horizontal collections. Each preview contains at most 16
unique titles, then a View All card. Up from a poster reaches its focusable View
All header in one press; Down on the header returns to the remembered poster.
Left at the start of a row opens the existing category data as a menu.

Both View All actions carry the same collection key and filter to the grid.
Sidebar provider categories use exactly the same key and pager as discovery.
Browse All uses the unfiltered eligible catalogue, not the discovery shortlist.
Ordinary category screens have no hero or horizontal shelves. Six columns are
used at normal TV width, dropping to five/four with a narrower usable layout.

The existing title page, cast photographs, related titles, favourites menus,
movie resume/playback and Series episode screen are reused. No trailer autoplay,
playback-engine, provider, Live TV or Sport Lounge changes are included.
Pinned More/Favourites/History screens retain their existing UI.

## Data and bounded loading

The existing category highlight readers supply previews on demand, only when
the corresponding row is composed. They retain the existing quality, language,
category visibility and duplicate rules. Preview artwork uses the existing Coil
poster components, sizing, cache and cancellation on disposal.

Only actual, visible provider folders matching common genre names become genre
shelves. No invented assignment of a title to a genre is added. Other folders
remain available in the category menu. Tiny genre previews disappear after
loading; no catalogue data is deleted to achieve this.

Trending uses the matched Home feed and its original ranks. Featured picks is
an explicitly finite editorial collection. Recent releases uses known original
release years from the current year and preceding two years. It does not claim
to be Recently Added. Their complete View All destinations are respectively all
eligible feed matches, the full current editorial selection, and all eligible
titles in the release-year window. The preview limit never becomes their full
catalogue limit.

`LoungeCataloguePagingSource` reads IDs using LIMIT/OFFSET on Room's reader
connection and hydrates only the current page. Curated IDs, release-year windows,
source and category constraints are applied before paging. The existing
visibility/customization transforms still apply. A database invalidation replaces
the source. Batches start at 60, prefetch at 18, and retain at most 300 provider
objects. Only visible poster views are composed. Manual/custom membership paths
continue to use existing DAO paging.

Plain catalogue orderings include a stable unique ID tie-breaker. The existing
provider order, A–Z, rating and provider-added-date controls remain; release year
newest first is added for All/provider folders. Unknown years/dates fall last.
Sorting retains the collection filter and resets its grid to the beginning.

## Navigation state

Discovery vertical position, each row's horizontal position, selected stable
poster ID, selected header/end card, grid position and selected ID are saved
through SaveableStateHolder. Series retains its browser state while the episode
screen is mounted. Returning from details refocuses the originating poster;
missing IDs fall back to the nearest saved position. Requester maps retain only
composed cards. Grid edges stop horizontal wrapping; Up from the first grid row
reaches sorting. Back from a full collection returns to discovery.

## Build and installation

Workflow: `Lounge TV VOD Navigation QA`, file `vod-navigation-qa.yml`.
The reusable core/playback/app unit-test job gates APK assembly.

The repository's persistent QA key is absent (`QA_KEYSTORE_BASE64` is missing).
Consequently this workflow uses an isolated Android debug signer and a NEW
package for every run/attempt:

`com.loungetv.player.vodnavigationqa.b<RUN_NUMBER>a<ATTEMPT>`

The visible app name is **Lounge TV VOD QA**. The artifact is
`lounge-tv-vod-navigation-qa`; `QA-IDENTITY.txt` records the exact commit, package,
version, certificate fingerprint and APK SHA-256. It contains no signing key.
This is a separate installation and requires its own login. It cannot update
production or another QA package, and does not clear any existing app data.
Do not uninstall anything to resolve a signature mismatch.

Download that exact artifact using GitHub Actions or `gh run download`, then:

```bash
adb connect 192.168.68.50:5555
adb -s 192.168.68.50:5555 install --no-incremental /path/to/app-standard-debug.apk
adb -s 192.168.68.50:5555 shell monkey -p EXACT_PACKAGE_FROM_QA_IDENTITY 1
```

## Validation boundary

Local Python guardrails, translation-literal inventory and whitespace checks are
run before upload. Android compilation and the new browse-rule JVM tests run in
CI together with the existing core, playback and app regression suites.

Passing compilation/unit tests does not validate physical Fire TV focus,
layout, memory or playback. A physical device is not reachable from this
workspace. The following still need checking before production approval:

- Six posters and unclipped focus at the user's Fire TV display/overscan setting.
- Header View All, end card, category menu, Back and repeated Movies/Series switching.
- Same poster and both scroll offsets after details, View All and Series episodes.
- Empty/small folders, missing artwork, loading images, and long category browsing
  beyond 300 titles, including reverse scrolling and a sort change.
- Existing English exclusions, favourites, progress, resume and episode playback.
- Memory and remote responsiveness after extended browsing, followed by playback.

No production release is authorized by this QA build.
