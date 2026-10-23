# Lounge TV customer TMDB gateway

## Purpose

A fresh Lounge TV install must display the native OwnTV Trending/Home metadata without asking customers to enter a TMDB API key. OwnTV Core `1.0.69` uses its own shared Worker when both advanced settings are blank. Fork builds without OwnTV's private edge access key fall back to its legacy Worker, which currently returns HTTP 403. This is not a BEST playlist issue.

## Implementation

- The existing TMDB caching proxy `extras/worker/tmdb/index.js` is deployed as a **separate** Cloudflare Worker at `https://metadata.loungetv.nz`.
- Its TMDB v3 API key is **only** a Cloudflare secret named `TMDB_KEY`. Never paste it in code, GitHub, Gradle, the APK, or build logs.
- `OwnTVApp.kt` seeds that public proxy URL in private app settings on first launch **only if** no personal TMDB API key or custom metadata server is configured. The one-time marker means a customer's later manual setting/clearing is respected. Existing configured installs are not overwritten.
- Metadata source precedence remains: customer's custom server > customer's own API key > Lounge TV managed gateway.
- The default Worker is not used by this build once the URL is seeded. An app update is needed because the setting is client-side. Do **not** publish an APK pointing at the new hostname before the Worker is live and tested.

## Deploy once, from your Mac (Cloudflare login required)

In the cloned repository, switch to branch `codex/fix-tmdb-default-20261023` and run:

```bash
cd extras/worker/tmdb
npx wrangler secret put TMDB_KEY
# Wrangler prompts privately for the TMDB v3 API key.
npx wrangler deploy
curl -fsS 'https://metadata.loungetv.nz/3/trending/movie/day?page=1' | python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d.get("results", [])) > 0; print("Trending movies:", len(d["results"]))'
curl -fsS 'https://metadata.loungetv.nz/3/trending/tv/day?page=1' | python3 -c 'import json,sys; d=json.load(sys.stdin); assert len(d.get("results", [])) > 0; print("Trending series:", len(d["results"]))'
curl -fsS 'https://metadata.loungetv.nz/3/search/movie?query=Oppenheimer' | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("results"); print("Movie search OK")'
```

Do not protect the `metadata.loungetv.nz` hostname with Cloudflare Access or a browser challenge: Fire TV makes machine-to-machine requests and cannot complete those challenges. Keep caching enabled, restrict external traffic with sensible Cloudflare rate limiting if needed, and monitor failures.

## Acceptance before release

1. Worker returns HTTP 200 JSON for both daily Trending feeds, a search, film details and TV details; no secret is in the response.
2. Build the **next** signed APK, version `5.2.2.5` (code `5020205`), rather than reusing the already signed `5.2.2.4` candidate.
3. Fresh Android TV emulator install, no TMDB settings touched; import existing BEST credentials and allow a completed catalogue sync. Verify native Trending hero, synopsis, artwork, trailers and playback.
4. Test an **update** on an existing configured install to confirm the private TMDB key/server, settings and profiles remain intact.
5. Confirm the version and signing fingerprint, then publish via the existing release workflow only after approval. The original `5.2.2.4` candidate is intentionally unchanged.

If the Worker is unavailable, the native app still shows provider catalogue and cached metadata but Trending enrichment cannot refresh. It will recover on future catalogue refresh when the Worker is restored.
