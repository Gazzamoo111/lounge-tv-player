import { verifiedEventAssets } from "./matcher.mjs";

// Isolated read-only QA endpoint. No user data, database calls, stream matching or redirects.
// Public key 123 is the documented TheSportsDB demo key. Optional server-side premium key.
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "content-type",
    "Access-Control-Allow-Methods": "GET,POST,OPTIONS" };
const reply = (data, status = 200) => Response.json(data, { status, headers: { ...cors, "Cache-Control": "no-store" } });
const cache = new Map();
const SIX_HOURS = 6 * 60 * 60_000;
const SEVEN_DAYS = 7 * 24 * 60 * 60_000;

function acceptFixture(x, now) {
    if (!x || typeof x !== "object") return null;
    const key = String(x.key || "").slice(0, 256);
    const title = String(x.title || "").slice(0, 180);
    const sport = String(x.sport || "").slice(0, 32);
    const feed = String(x.feed || "").slice(0, 80);
    const startUtcMs = Number(x.startUtcMs);
    if (!key || !title || !/^[a-z-]+$/.test(sport) || !Number.isFinite(startUtcMs) ||
        startUtcMs < now - 3600_000 || startUtcMs > now + SEVEN_DAYS) return null;
    const teams = (Array.isArray(x.teams) ? x.teams : []).slice(0, 2).map(t => ({
        name: String(t?.name || "").slice(0, 80),
        shortName: String(t?.shortName || "").slice(0, 80),
    })).filter(t => t.name);
    if (teams.length !== 2 && sport !== "racing") return null;
    return { key, title, sport, feed, startUtcMs, teams };
}
async function searchSportDB(fixture, order) {
    const key = Deno.env.get("THESPORTSDB_API_KEY") || "123";
    const q = fixture.sport === "racing" ? fixture.title :
        order === 0 ? fixture.teams[0].name + " vs " + fixture.teams[1].name :
            fixture.teams[1].name + " vs " + fixture.teams[0].name;
    const u = new URL("https://www.thesportsdb.com/api/v1/json/" + encodeURIComponent(key) + "/searchevents.php");
    u.searchParams.set("e", q);
    const response = await fetch(u, { headers: { accept: "application/json" }, signal: AbortSignal.timeout(3400) });
    if (!response.ok) return [];
    const body = await response.json();
    return Array.isArray(body?.event) ? body.event :
        Array.isArray(body?.events) ? body.events : [];
}
async function findOne(fixture) {
    const cached = cache.get(fixture.key);
    if (cached && cached.startedAt === fixture.startUtcMs && Date.now() - cached.checked < SIX_HOURS)
        return cached.art;
    let art = null;
    // Free TheSportsDB key returns at most one item per search. Check both fixture orders.
    for (let order = 0; order < (fixture.sport === "racing" ? 1 : 2); order++) {
        try {
            const candidates = await searchSportDB(fixture, order);
            art = candidates.map(c => verifiedEventAssets(fixture, c)).find(Boolean) || null;
            if (art) break;
        } catch { /* providers can fail; use the guaranteed crest fallback */ }
    }
    cache.set(fixture.key, { art, startedAt: fixture.startUtcMs, checked: Date.now() });
    if (cache.size > 120) for (const key of [...cache.keys()].slice(0, 30)) cache.delete(key);
    return art;
}
Deno.serve(async req => {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
    if (req.method === "GET") return reply({ status: "ok", mode: "qa-read-only", source: "thesportsdb" });
    if (req.method !== "POST") return reply({ error: "method_not_allowed" }, 405);
    try {
        if (Number(req.headers.get("content-length") || "0") > 20000) return reply({ error: "too_large" }, 413);
        const text = await req.text();
        if (text.length > 20000) return reply({ error: "too_large" }, 413);
        const body = JSON.parse(text);
        const now = Date.now();
        const events = Array.isArray(body.events) ? body.events : [];
        const fixtures = events.slice(0, 12).map(x => acceptFixture(x, now)).filter(Boolean);
        const arts = [];
        // Bound upstream load, including free-key rate limits.
        for (let i = 0; i < fixtures.length; i += 4) {
            const group = fixtures.slice(i, i + 4);
            const found = await Promise.all(group.map(async fixture => {
                const art = await findOne(fixture);
                return art ? { key: fixture.key, ...art } : null;
            }));
            arts.push(...found.filter(Boolean));
        }
        return reply({ status: "ok", asOf: new Date().toISOString(),
            arts, diagnostics: { requested: events.length, scanned: fixtures.length, exact: arts.length } });
    } catch {
        return reply({ error: "invalid_request" }, 400);
    }
});