const ESPN = "https://site.api.espn.com/apis/site/v2/sports";
const MAX_WINDOW_MS = 72 * 60 * 60 * 1000;

export const FEEDS = Object.freeze([
  { sport: "nfl", path: "football", league: "nfl" },
  { sport: "league", path: "rugby-league", league: "3" },
  { sport: "rugby", path: "rugby", league: "289234" },
  { sport: "basketball", path: "basketball", league: "nbl" },
  { sport: "basketball", path: "basketball", league: "nba" },
  { sport: "motorsport", path: "racing", league: "f1" },
  { sport: "combat", path: "mma", league: "ufc" },
]);

export function parseQuery(url, now = Date.now()) {
  const from = parseInstant(url.searchParams.get("from")) ?? now - 6 * 60 * 60 * 1000;
  const to = parseInstant(url.searchParams.get("to")) ?? now + 36 * 60 * 60 * 1000;
  if (to <= from || to - from > MAX_WINDOW_MS) throw new RangeError("Fixture window must be greater than zero and no longer than 72 hours");
  const sport = url.searchParams.get("sport")?.trim().toLowerCase() || null;
  if (sport && !FEEDS.some((feed) => feed.sport === sport)) throw new RangeError("Unsupported sport filter");
  return { from, to, sport };
}

export function normalizeEspnEvent(raw, feed, fetchedAt) {
  const competition = raw.competitions?.[0] ?? {};
  const state = normalizeState(raw.status?.type?.state, raw.status?.type?.name);
  return {
    id: `espn:${feed.path}/${feed.league}#${String(raw.id)}`,
    source: "espn",
    externalId: String(raw.id),
    sport: feed.sport,
    leagueId: `${feed.path}/${feed.league}`,
    leagueName: raw.league?.name ?? raw.season?.name ?? feed.league.toUpperCase(),
    startUtcMs: Date.parse(raw.date),
    state,
    statusText: raw.status?.type?.shortDetail ?? raw.status?.type?.detail ?? state,
    competitors: (competition.competitors ?? []).map((side) => ({
      id: String(side.team?.id ?? side.id ?? ""),
      name: side.team?.displayName ?? side.team?.name ?? "",
      shortName: side.team?.shortDisplayName ?? side.team?.name ?? "",
      abbreviation: side.team?.abbreviation ?? "",
      crestUrl: side.team?.logo ?? null,
      home: side.homeAway === "home",
      score: side.score ?? null,
    })),
    sourceUpdatedAtMs: fetchedAt,
    lastSuccessfulUpdateMs: fetchedAt,
  };
}

export function dedupe(events) {
  const byId = new Map();
  for (const event of events) {
    const old = byId.get(event.id);
    const rank = stateRank(event.state);
    const oldRank = old ? stateRank(old.state) : -1;
    if (!old || rank > oldRank || (rank === oldRank && event.sourceUpdatedAtMs > old.sourceUpdatedAtMs)) byId.set(event.id, event);
  }
  return [...byId.values()].sort((a, b) => a.startUtcMs - b.startUtcMs || a.id.localeCompare(b.id));
}

export async function collectEvents(query, fetcher = fetch, fetchedAt = Date.now()) {
  const feeds = FEEDS.filter((feed) => !query.sport || feed.sport === query.sport);
  const days = utcDays(query.from, query.to);
  const results = await Promise.all(feeds.flatMap((feed) => days.map(async (day) => {
    const endpoint = `${ESPN}/${feed.path}/${feed.league}/scoreboard?dates=${day}&limit=100`;
    try {
      const response = await fetcher(endpoint, { headers: { accept: "application/json" } });
      if (!response.ok) return { feed, ok: false, status: response.status, events: [] };
      const payload = await response.json();
      const events = (payload.events ?? []).map((event) => normalizeEspnEvent(event, feed, fetchedAt));
      return { feed, ok: true, status: response.status, events };
    } catch (error) {
      return { feed, ok: false, status: 0, error: error instanceof Error ? error.message : String(error), events: [] };
    }
  })));
  const failed = results.filter((result) => !result.ok).map((result) => ({
    feed: `${result.feed.path}/${result.feed.league}`,
    status: result.status,
  }));
  const events = dedupe(results.flatMap((result) => result.events))
    .filter((event) => event.startUtcMs >= query.from && event.startUtcMs <= query.to);
  return { events, failed, requestedFeeds: feeds.length, fetchedAt };
}

async function handle(request) {
  const url = new URL(request.url);
  if (url.pathname !== "/v1/sports/events") return json({ error: "Not found" }, 404, "no-store");
  if (request.method !== "GET" && request.method !== "HEAD") return json({ error: "Method not allowed" }, 405, "no-store", { allow: "GET, HEAD" });
  let query;
  try {
    query = parseQuery(url);
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : "Invalid query" }, 400, "no-store");
  }
  const result = await collectEvents(query);
  if (result.failed.length === result.requestedFeeds * utcDays(query.from, query.to).length) {
    console.error(JSON.stringify({ message: "all fixture feeds failed", failed: result.failed }));
    return json({ error: "Fixture sources are temporarily unavailable", lastUpdated: null }, 503, "no-store", { "retry-after": "300" });
  }
  if (result.failed.length) console.warn(JSON.stringify({ message: "partial fixture coverage", failed: result.failed }));
  const body = {
    events: result.events,
    lastUpdated: new Date(result.fetchedAt).toISOString(),
    status: result.failed.length ? "partial" : "ok",
    diagnostics: { requestedFeeds: result.requestedFeeds, failedFeeds: result.failed },
  };
  const response = json(body, 200, "public, max-age=120, stale-while-revalidate=600, stale-if-error=3600");
  return request.method === "HEAD" ? new Response(null, response) : response;
}

function parseInstant(value) {
  if (!value) return null;
  const numeric = Number(value);
  if (Number.isFinite(numeric) && numeric > 0) return numeric;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function utcDays(from, to) {
  const days = [];
  const cursor = new Date(from);
  cursor.setUTCHours(0, 0, 0, 0);
  while (cursor.getTime() <= to) {
    days.push(cursor.toISOString().slice(0, 10).replaceAll("-", ""));
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return days;
}

function normalizeState(state, name) {
  if (name === "STATUS_POSTPONED") return "postponed";
  if (name === "STATUS_CANCELED" || name === "STATUS_CANCELLED") return "cancelled";
  if (state === "in") return "live";
  if (state === "post") return "finished";
  return "scheduled";
}

function stateRank(state) {
  return { scheduled: 0, live: 1, finished: 2, postponed: 3, cancelled: 4 }[state] ?? -1;
}

function json(body, status, cacheControl, extra = {}) {
  return Response.json(body, {
    status,
    headers: {
      "cache-control": cacheControl,
      "content-type": "application/json; charset=utf-8",
      ...extra,
    },
  });
}

export default { fetch: handle };
