# NZ gateway repair — staged, not deployed

The audited production edge is `lounge-core-xtream` version 50. The provider-reported bundle SHA-256 is `ec3385ec20c0c979214cc35a676527d040e079909b159342798a00f2491dc7b2`; the downloaded `index.ts` file SHA-256 is `815d293aec1fd924efc83e5e8b5ea89b6b06ad292b0023fe4377ec61fb5093cb`. These identify different objects. `fixtures/lounge-core-xtream-v50.ts` preserves that exact source, recovered by reversing both staged patches and independently matched against a fresh v50 download. The fixture has no embedded database URL, token or account credential; those are environment/request values.

Its `/live` handler ignores the release binding's provider and redirects every stream ID to BEST. The active release binds five Freeview rows to MJH NZ using synthetic IDs 900000001–900000005. The Android app overrides these URLs locally; external clients cannot do that.

`gateway-nz-routing.patch` applies to that exact downloaded edge source. Copy `nz-routing.mjs` beside `index.ts`. The patch carries provider identity into the playback and M3U queries, authenticates the account before non-BEST routing, routes the three clear HLS sources to their existing public URLs, and preserves TVNZ Widevine metadata in compatible M3U output. Generic Xtream clients receive an explicit `drm_client_required` error for TVNZ rather than a fabricated BEST URL. This does not remove geographic or DRM requirements.

Validation performed: patch applicability against the retrieved v50 source; three Node routing/metadata contract tests; public-source GETs returned 200 for Three, Whakaata Māori and Te Reo manifests. TVNZ 1 and 2 returned 403 with “Access restricted based on geographic location” from this test host. Manifest HTTP 200 does not establish sustained decoding or segment playback.

Before production: deploy to an isolated QA function, run authenticated API/M3U/redirect and segment/decoder checks with a designated QA line in the intended region, test a non-NZ control channel in the same third-party clients, and obtain approval for production deployment. No production function is modified by these patches or the local preparation/test scripts.

`tennis-allowlist.patch` separately restores a bounded `tennis` token predicate while preserving present-inventory and authenticated `/sport` checks. Read-only SQL confirmed that 79 present tennis-named channels in categories 2176/2403 fail the current name predicate and satisfy the staged predicate. Those categories contain another 51 present rows whose names are not proved by this change; they are not admitted by a broad category override. The patch changes eligibility only, not fixture evidence or a claim of playable content.


## Executable full-handler contracts

Run with Node **22.17.0 or later**:

```sh
node --test qa/gateway/*.test.mjs
```

There are 20 gateway tests: five existing source/helper contracts and 15 new full-handler/QA-isolation contracts. `gateway-harness.mjs` verifies the pristine file hash, applies both real patches with zero fuzz in a temporary directory, strips TypeScript using Node's `stripTypeScriptTypes`, and evaluates the entire handler in a VM. Only its two imports are substituted. `Deno.serve` captures the original callback; the tests call it with real Request/Response objects. No gateway function body is replaced for these full-handler tests.

SQL and fetch are deterministic stubs; unknown SQL or network targets fail. The sports stub evaluates the actual SQL name predicate, translates its POSIX alphanumeric class for ASCII fixture names, and preserves live/present/ID/category checks. This is executable routing/authentication/format evidence, **not PostgreSQL engine execution**, provider authentication, segment decoding, geographic availability, DRM licensing or Fire TV playback evidence.

The suite reproduces the original synthetic-ID defect, verifies the repaired clear-HLS routes and denied unauthenticated requests, preserves BEST's existing provider-side authentication/redirect contract, checks both TVNZ 422 responses, checks stable Xtream stream/category schema and M3U DRM metadata, and denies missing releases/mappings/providers. Tennis cases cover both omitted categories, existing sports, unrelated broadcaster names, absent/non-live inventory, authentication, and false-positive substrings such as `Tennison Cinema` and `TableTennis`. The added tennis alternative is `(^|[^[:alnum:]])tennis([^[:alnum:]]|$)`; all pre-existing sport alternatives remain unchanged. The 79 current named tennis rows contain standalone Tennis tokens, including `:Tennis NN` and BTN+ `Tennis (M/W)` names.

## Prepare an isolated QA clone

```sh
node qa/gateway/prepare-qa-gateway.mjs /path/to/local/output lounge-core-xtream-qa-pass
```

This writes `index.ts`, `nz-routing.mjs` and a hash manifest locally. It does not deploy or contact a service. The preparation accepts only a distinct `lounge-core-xtream-qa[-suffix]` function name, rewrites its own routes/playlist URLs, disables production auth-cache writes, and adds `X-Lounge-QA: staged-v50-nz-tennis` to every response. It permits GET/OPTIONS for Live, Sport, M3U and account/live-category/live-stream Xtream requests. Other routes/actions, including VOD and EPG, return 404. The fixture's unused VOD/EPG functions remain in the source but cannot be reached through the QA wrapper. A forwarded-host query cannot point generated QA playlist entries to production. Two executable contracts exercise this wrapper and assert no auth-cache write occurs.

An actual isolated deployment and designated-account regional/decoder tests remain separate verification steps. Never deploy this QA wrapper over the production function. Production approval is required after QA and physical acceptance.
