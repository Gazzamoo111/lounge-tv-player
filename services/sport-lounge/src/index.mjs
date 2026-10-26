const ESPN = "https://site.api.espn.com/apis/site/v2/sports";
const ESPN_CRICKET = "https://site.web.api.espn.com/apis/v2/scoreboard/header";
const MAX_WINDOW_MS = 72 * 60 * 60 * 1000;
const CACHE_CONTROL = "public, max-age=120, stale-while-revalidate=600, stale-if-error=3600";
const KNOWN_SPORTS = new Set(["nfl", "league", "rugby", "cricket", "basketball", "netball", "football", "combat", "motorsport"]);

/** Public, no-secret ESPN feeds already supported by OwnTV's sports integration. */
export const FEEDS = Object.freeze([
  { sport: "nfl", modelSport: "football", path: "football", league: "nfl", name: "NFL" },
  { sport: "league", modelSport: "rugby-league", path: "rugby-league", league: "3", name: "National Rugby League" },
  { sport: "rugby", modelSport: "rugby", path: "rugby", league: "289234", name: "International Rugby" },
  { sport: "cricket", modelSport: "cricket", path: "cricket", league: "all", name: "Cricket", kind: "cricket-header" },
  { sport: "basketball", modelSport: "basketball", path: "basketball", league: "nbl", name: "National Basketball League" },
  { sport: "basketball", modelSport: "basketball", path: "basketball", league: "nba", name: "NBA" },
  { sport: "football", modelSport: "soccer", path: "soccer", league: "aus.1", name: "Australian A-League Men" },
  { sport: "football", modelSport: "soccer", path: "soccer", league: "eng.1", name: "English Premier League" },
  { sport: "football", modelSport: "soccer", path: "soccer", league: "uefa.champions", name: "UEFA Champions League" },
  { sport: "motorsport", modelSport: "racing", path: "racing", league: "f1", name: "Formula 1" },
  { sport: "combat", modelSport: "mma", path: "mma", league: "ufc", name: "UFC" },
]);

export class ProviderBackoff {
  constructor() { this.failures = new Map(); }

  remaining(feedId, now) {
    return Math.max(0, (this.failures.get(feedId)?.retryAt ?? 0) - now);
  }

  fail(feedId, status, now, retryAfter) {
    if (status !== 403 && status !== 429) return;
    const attempts = (this.failures.get(feedId)?.attempts ?? 0) + 1;
    const retryAfterMs = parseRetryAfter(retryAfter, now);
    const exponentialMs = Math.min(60 * 60_000, 5 * 60_000 * (2 ** Math.min(attempts - 1, 4)));
    this.failures.set(feedId, { attempts, retryAt: now + (retryAfterMs ?? exponentialMs) });
  }

  success(feedId) { this.failures.delete(feedId); }
}

export class MemorySnapshotStore {
  constructor() { this.snapshots = new Map(); }
  async read(key) { return this.snapshots.get(key) ?? null; }
  async write(key, value) { this.snapshots.set(key, structuredClone(value)); }
}

class CloudflareSnapshotStore {
  async read(key) {
    const response = await globalThis.caches.default.match(snapshotRequest(key));
    return response?.ok ? response.json() : null;
  }

  async write(key, value) {
    await globalThis.caches.default.put(snapshotRequest(key), json(value, 200, "max-age=3600"));
  }
}

export function parseQuery(url, now = Date.now()) {
  const from = parseInstant(url.searchParams.get("from")) ?? now - 6 * 60 * 60 * 1000;
  const to = parseInstant(url.searchParams.get("to")) ?? now + 36 * 60 * 60 * 1000;
  if (to <= from || to - from > MAX_WINDOW_MS) throw new RangeError("Fixture window must be greater than zero and no longer than 72 hours");
  const sport = url.searchParams.get("sport")?.trim().toLowerCase() || null;
  if (sport && !KNOWN_SPORTS.has(sport)) throw new RangeError("Unsupported sport filter");
  return { from, to, sport };
}

export function normalizeEspnEvent(raw, feed, fetchedAt) {
  const id = textOf(raw?.id);
  const startUtcMs = Date.parse(raw?.date);
  if (!id || !Number.isFinite(startUtcMs)) return null;
  const competition = raw.competitions?.[0] ?? {};
  const state = normalizeState(raw.status?.type?.state, raw.status?.type?.name);
  const competitors = (competition.competitors ?? []).map(normalizeStandardCompetitor).filter(Boolean);
  return eventContract({
    id, feed,
    leagueName: raw.league?.name ?? feed.name ?? raw.season?.name,
    title: raw.name ?? raw.shortName ?? competitorTitle(competitors),
    subtitle: raw.shortName ?? null,
    startUtcMs, state,
    statusText: raw.status?.type?.shortDetail ?? raw.status?.type?.detail ?? state,
    competitors, fetchedAt,
  });
}

/** Cricket uses ESPN's scoreboard-header schema, not the standard site scoreboard schema. */
export function normalizeCricketEvent(raw, league, feed, fetchedAt) {
  const id = textOf(raw?.id ?? raw?.competitionId);
  const startUtcMs = Date.parse(raw?.date);
  if (!id || !Number.isFinite(startUtcMs)) return null;
  const state = normalizeState(raw.fullStatus?.type?.state ?? raw.status, raw.fullStatus?.type?.name);
  const competitors = (raw.competitors ?? []).map(normalizeCricketCompetitor).filter(Boolean);
  return eventContract({
    id,
    feed,
    leagueId: `${feed.path}/${textOf(league?.id) || feed.league}`,
    leagueSlug: textOf(league?.id) || feed.league,
    leagueName: league?.name ?? league?.shortName ?? feed.name,
    title: raw.name ?? raw.shortName ?? competitorTitle(competitors),
    subtitle: raw.description ?? raw.title ?? null,
    startUtcMs, state,
    statusText: raw.fullStatus?.type?.shortDetail ?? raw.fullStatus?.summary ?? raw.summary ?? state,
    competitors, fetchedAt,
  });
}

export function dedupe(events) {
  const byId = new Map();
  for (const event of events.filter(Boolean)) {
    const old = byId.get(event.id);
    if (!old || preferEvent(event, old)) byId.set(event.id, event);
  }
  return [...byId.values()].sort((a, b) => a.startUtcMs - b.startUtcMs || a.id.localeCompare(b.id));
}

export async function collectEvents(query, fetcher = fetch, fetchedAt = Date.now(), backoff = new ProviderBackoff()) {
  const feeds = FEEDS.filter((feed) => !query.sport || feed.sport === query.sport);
  const days = utcDays(query.from, query.to);
  const requests = feeds.flatMap((feed) => days.map((day) => ({ feed, day })));
  const results = await Promise.all(requests.map(({ feed, day }) => fetchFeed(feed, day, fetcher, fetchedAt, backoff)));
  const failed = results.filter((result) => !result.ok).map((result) => ({
    feed: feedId(result.feed), status: result.status, retryAfterMs: result.retryAfterMs || undefined,
  }));
  const events = dedupe(results.flatMap((result) => result.events))
    .filter((event) => event.startUtcMs >= query.from && event.startUtcMs <= query.to);
  return {
    events, failed, requestedFeeds: feeds.length, requestedRequests: requests.length,
    successfulRequests: results.filter((result) => result.ok).length, fetchedAt,
  };
}

export function createGateway({ fetcher = fetch, store = defaultStore(), backoff = new ProviderBackoff(), now = Date.now } = {}) {
  return async function handle(request) {
    const url = new URL(request.url);
    if (url.pathname !== "/v1/sports/events") return json({ error: "Not found" }, 404, "no-store");
    if (request.method !== "GET" && request.method !== "HEAD") return json({ error: "Method not allowed" }, 405, "no-store", { allow: "GET, HEAD" });
    let query;
    try { query = parseQuery(url, now()); }
    catch (error) { return json({ error: error instanceof Error ? error.message : "Invalid query" }, 400, "no-store"); }

    const key = snapshotKey(query);
    const cached = await safeStore(() => store.read(key));
    const result = await collectEvents(query, fetcher, now(), backoff);
    const failedFeedIds = new Set(result.failed.map((failure) => failure.feed));
    const events = mergeSafeStale(result.events, cached?.events ?? [], failedFeedIds, query);
    const unsupportedSport = Boolean(query.sport && !FEEDS.some((feed) => feed.sport === query.sport));

    if (result.requestedRequests > 0 && result.successfulRequests === 0 && events.length === 0) {
      console.error(JSON.stringify({ message: "all fixture feeds failed", failed: result.failed }));
      return json({ error: "Fixture sources are temporarily unavailable", lastUpdated: cached?.lastUpdated ?? null }, 503, "no-store", { "retry-after": "300" });
    }

    const stale = events.some((event) => failedFeedIds.has(event.feedId));
    const status = unsupportedSport ? "unsupported" : stale && result.successfulRequests === 0 ? "stale" : result.failed.length ? "partial" : "ok";
    const body = {
      events,
      lastUpdated: result.successfulRequests > 0 ? new Date(result.fetchedAt).toISOString() : cached?.lastUpdated ?? null,
      status,
      diagnostics: {
        requestedFeeds: result.requestedFeeds, requestedRequests: result.requestedRequests,
        failedFeeds: result.failed,
        staleFeeds: [...failedFeedIds].filter((id) => events.some((event) => event.feedId === id)),
        unsupportedSport: unsupportedSport ? query.sport : null,
      },
    };
    if (result.successfulRequests > 0) await safeStore(() => store.write(key, body));
    if (result.failed.length) console.warn(JSON.stringify({ message: "partial fixture coverage", failed: result.failed }));
    const response = json(body, 200, CACHE_CONTROL);
    return request.method === "HEAD" ? new Response(null, response) : response;
  };
}

async function fetchFeed(feed, day, fetcher, fetchedAt, backoff) {
  const id = feedId(feed);
  const remaining = backoff.remaining(id, fetchedAt);
  if (remaining > 0) return { feed, ok: false, status: 429, retryAfterMs: remaining, events: [] };
  const endpoint = feed.kind === "cricket-header"
    ? `${ESPN_CRICKET}?sport=cricket&region=us&lang=en&contentorigin=espn&tz=UTC&dates=${day}`
    : `${ESPN}/${feed.path}/${feed.league}/scoreboard?dates=${day}&limit=100`;
  try {
    const response = await fetcher(endpoint, { headers: { accept: "application/json" } });
    if (!response.ok) {
      backoff.fail(id, response.status, fetchedAt, response.headers.get("retry-after"));
      return { feed, ok: false, status: response.status, retryAfterMs: backoff.remaining(id, fetchedAt), events: [] };
    }
    const payload = await response.json();
    backoff.success(id);
    const events = feed.kind === "cricket-header"
      ? (payload.sports ?? []).flatMap((sport) => (sport.leagues ?? []).flatMap((league) =>
        (league.events ?? []).map((event) => normalizeCricketEvent(event, league, feed, fetchedAt))))
      : (payload.events ?? []).map((event) => normalizeEspnEvent(event, feed, fetchedAt));
    return { feed, ok: true, status: response.status, events: events.filter(Boolean) };
  } catch (error) {
    return { feed, ok: false, status: 0, error: error instanceof Error ? error.message : String(error), events: [] };
  }
}

function eventContract({ id, feed, leagueId = feedId(feed), leagueSlug = feed.league, leagueName, title, subtitle, startUtcMs, state, statusText, competitors, fetchedAt }) {
  const sourceFeedId = feedId(feed);
  return {
    id: `espn:${sourceFeedId}#${id}`, source: "espn", externalId: id, sport: feed.modelSport ?? feed.sport,
    leagueId, leagueName: leagueName || feed.name, feedId: sourceFeedId, leagueSlug,
    title: title || competitorTitle(competitors) || feed.name, subtitle, startUtcMs, state, statusText,
    competitors, sourceUpdatedAtMs: fetchedAt, lastSuccessfulUpdateMs: fetchedAt,
  };
}

function normalizeStandardCompetitor(side) {
  const entity = side?.team ?? side?.athlete ?? {};
  const id = textOf(entity.id ?? side?.id);
  const name = entity.displayName ?? entity.fullName ?? entity.name ?? side?.displayName ?? side?.name;
  if (!id && !name) return null;
  return {
    id, name: name ?? "", shortName: entity.shortDisplayName ?? entity.name ?? name ?? "",
    abbreviation: entity.abbreviation ?? "", crestUrl: entity.logo ?? entity.headshot?.href ?? null,
    home: side?.homeAway === "home", score: side?.score == null ? null : String(side.score),
  };
}

function normalizeCricketCompetitor(side) {
  const id = textOf(side?.id);
  const name = side?.displayName ?? side?.name;
  if (!id && !name) return null;
  return {
    id, name: name ?? "", shortName: side?.name ?? name ?? "", abbreviation: side?.abbreviation ?? "",
    crestUrl: side?.logo ?? null, home: side?.homeAway === "home", score: side?.score ? String(side.score) : null,
  };
}

function mergeSafeStale(fresh, cached, failedFeedIds, query) {
  const freshIds = new Set(fresh.map((event) => event.id));
  const stale = cached.filter((event) =>
    failedFeedIds.has(event.feedId) && !freshIds.has(event.id) &&
    event.startUtcMs >= query.from && event.startUtcMs <= query.to);
  return dedupe([...fresh, ...stale]);
}

function preferEvent(next, old) {
  const oldTerminal = isTerminal(old.state);
  const nextTerminal = isTerminal(next.state);
  if (oldTerminal && !nextTerminal) return false;
  if (!oldTerminal && nextTerminal) return true;
  if (stateRank(next.state) !== stateRank(old.state) && !oldTerminal && !nextTerminal) return stateRank(next.state) > stateRank(old.state);
  return next.sourceUpdatedAtMs > old.sourceUpdatedAtMs;
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
  const normalizedName = String(name ?? "").toUpperCase();
  if (normalizedName.includes("POSTPONED") || state === "postponed") return "postponed";
  if (normalizedName.includes("CANCELED") || normalizedName.includes("CANCELLED") || state === "cancelled" || state === "canceled") return "cancelled";
  if (state === "in") return "live";
  if (state === "post") return "finished";
  return "scheduled";
}

function stateRank(state) { return { scheduled: 0, live: 1, finished: 2, postponed: 2, cancelled: 2 }[state] ?? -1; }
function isTerminal(state) { return state === "finished" || state === "postponed" || state === "cancelled"; }

function parseRetryAfter(value, now) {
  if (!value) return null;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return seconds * 1000;
  const instant = Date.parse(value);
  return Number.isFinite(instant) ? Math.max(0, instant - now) : null;
}

function competitorTitle(competitors) { return competitors.map((side) => side.name).filter(Boolean).join(" v "); }
function textOf(value) { return value == null ? "" : String(value); }
function feedId(feed) { return `${feed.path}/${feed.league}`; }
function snapshotKey(query) { return `${query.from}:${query.to}:${query.sport ?? "all"}`; }
function snapshotRequest(key) { return new Request(`https://sport-lounge-cache.invalid/${encodeURIComponent(key)}`); }
function defaultStore() { return globalThis.caches?.default ? new CloudflareSnapshotStore() : new MemorySnapshotStore(); }

async function safeStore(action) {
  try { return await action(); }
  catch (error) {
    console.warn(JSON.stringify({ message: "fixture cache unavailable", error: error instanceof Error ? error.message : String(error) }));
    return null;
  }
}

function json(body, status, cacheControl, extra = {}) {
  return Response.json(body, { status, headers: { "cache-control": cacheControl, "content-type": "application/json; charset=utf-8", ...extra } });
}

const handle = createGateway();
export default { fetch: handle };
