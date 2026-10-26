import assert from "node:assert/strict";
import test from "node:test";
import {
  collectEvents,
  createGateway,
  dedupe,
  MemorySnapshotStore,
  normalizeCricketEvent,
  normalizeEspnEvent,
  parseQuery,
  ProviderBackoff,
} from "../src/index.mjs";

test("query rejects windows longer than 72 hours and unknown sports but accepts transparent netball gap", () => {
  assert.throws(() => parseQuery(new URL("https://example.test/v1/sports/events?from=0&to=9999999999999"), 1_000), RangeError);
  assert.equal(parseQuery(new URL("https://example.test/v1/sports/events?sport=netball"), 1_000).sport, "netball");
  assert.throws(() => parseQuery(new URL("https://example.test/v1/sports/events?sport=quidditch"), 1_000), RangeError);
});

test("normalizer creates stable provider id and never a watchability claim", () => {
  const event = normalizeEspnEvent({
    id: "42",
    date: "2026-10-26T00:20:00Z",
    status: { type: { state: "in", shortDetail: "Q2" } },
    competitions: [{ competitors: [] }],
  }, { sport: "nfl", modelSport: "football", path: "football", league: "nfl" }, 5_000);
  assert.equal(event.id, "espn:football/nfl#42");
  assert.equal(event.state, "live");
  assert.equal(event.sport, "football");
  assert.equal("watchable" in event, false);
  assert.equal("channelId" in event, false);
});

test("standard normalizer supports athlete competitors used by combat and motorsport", () => {
  const event = normalizeEspnEvent({
    id: "ufc-1",
    name: "Fighter A v Fighter B",
    date: "2026-10-26T04:00:00Z",
    status: { type: { state: "pre" } },
    competitions: [{ competitors: [{
      id: "a", homeAway: "home", athlete: { id: "a", displayName: "Fighter A", headshot: { href: "https://example.test/a.png" } },
    }] }],
  }, { sport: "combat", modelSport: "mma", path: "mma", league: "ufc", name: "UFC" }, 5_000);
  assert.equal(event.competitors[0].name, "Fighter A");
  assert.equal(event.competitors[0].crestUrl, "https://example.test/a.png");
  assert.equal(event.sport, "mma");
});

test("dedupe rejects out-of-order scheduled regression", () => {
  const base = { id: "espn:x#1", startUtcMs: 2, sourceUpdatedAtMs: 2 };
  assert.equal(dedupe([{ ...base, state: "finished" }, { ...base, state: "scheduled", sourceUpdatedAtMs: 3 }])[0].state, "finished");
});

test("newer terminal correction wins but an older cancellation does not", () => {
  const base = { id: "espn:x#1", startUtcMs: 2 };
  const finished = { ...base, state: "finished", sourceUpdatedAtMs: 4 };
  const oldCancellation = { ...base, state: "cancelled", sourceUpdatedAtMs: 3 };
  const correctedPostponement = { ...base, state: "postponed", sourceUpdatedAtMs: 5 };
  assert.equal(dedupe([finished, oldCancellation])[0].state, "finished");
  assert.equal(dedupe([finished, correctedPostponement])[0].state, "postponed");
});

test("cricket header normalizer preserves series, stable id, competitors, score and UTC time", () => {
  const event = normalizeCricketEvent({
    id: "1546580",
    date: "2026-10-26T02:45:00Z",
    name: "Sikkim v Nagaland",
    description: "Plate Group, Ranji Trophy",
    status: "pre",
    fullStatus: { type: { state: "pre", shortDetail: "Scheduled" } },
    competitors: [
      { id: "2085", displayName: "Sikkim", name: "SIKM", homeAway: "home", score: "123/4" },
      { id: "1156781", displayName: "Nagaland", name: "NAGA", homeAway: "away", score: "" },
    ],
  }, { id: "8050", name: "Ranji Trophy" }, {
    sport: "cricket", path: "cricket", league: "all", name: "Cricket", kind: "cricket-header",
  }, 5_000);
  assert.equal(event.id, "espn:cricket/all#1546580");
  assert.equal(event.leagueId, "cricket/8050");
  assert.equal(event.feedId, "cricket/all");
  assert.equal(event.leagueName, "Ranji Trophy");
  assert.equal(event.startUtcMs, Date.parse("2026-10-26T02:45:00Z"));
  assert.equal(event.competitors[0].score, "123/4");
  assert.equal(event.state, "scheduled");
});

test("collector reports partial provider failure without inventing rows", async () => {
  let request = 0;
  const fetcher = async () => {
    request += 1;
    if (request === 1) return new Response("upstream", { status: 429 });
    return Response.json({ events: [] });
  };
  const result = await collectEvents({ from: Date.parse("2026-10-26T00:00:00Z"), to: Date.parse("2026-10-26T01:00:00Z"), sport: "nfl" }, fetcher, 10);
  assert.equal(result.events.length, 0);
  assert.equal(result.failed.length, 1);
  assert.equal(result.failed[0].status, 429);
});

test("403 and 429 pause only the exact feed without repeatedly polling it", async () => {
  const backoff = new ProviderBackoff();
  let requests = 0;
  const fetcher = async () => {
    requests += 1;
    return new Response("limited", { status: 429, headers: { "retry-after": "600" } });
  };
  const query = { from: Date.parse("2026-10-26T00:00:00Z"), to: Date.parse("2026-10-26T01:00:00Z"), sport: "nfl" };
  const first = await collectEvents(query, fetcher, 1_000, backoff);
  const second = await collectEvents(query, fetcher, 2_000, backoff);
  assert.equal(requests, 1);
  assert.equal(first.failed[0].status, 429);
  assert.equal(second.failed[0].retryAfterMs, 599_000);
});

test("gateway returns exact-feed stale cache on provider failure and does not claim watchability", async () => {
  const clock = { now: 1_000 };
  let failing = false;
  const fetcher = async () => failing
    ? new Response("limited", { status: 429, headers: { "retry-after": "300" } })
    : Response.json({ events: [{
      id: "42",
      name: "New York Jets at New England Patriots",
      date: "2026-10-26T00:20:00Z",
      status: { type: { state: "pre", shortDetail: "Scheduled" } },
      competitions: [{ competitors: [] }],
    }] });
  const gateway = createGateway({ fetcher, store: new MemorySnapshotStore(), now: () => clock.now });
  const url = "https://example.test/v1/sports/events?from=2026-10-26T00:00:00Z&to=2026-10-26T01:00:00Z&sport=nfl";
  const fresh = await (await gateway(new Request(url))).json();
  failing = true;
  clock.now += 1_000;
  const staleResponse = await gateway(new Request(url));
  const stale = await staleResponse.json();
  assert.equal(staleResponse.status, 200);
  assert.equal(stale.status, "stale");
  assert.equal(stale.events[0].id, fresh.events[0].id);
  assert.equal("watchable" in stale.events[0], false);
  assert.deepEqual(stale.diagnostics.staleFeeds, ["football/nfl"]);
});

test("known but unsupported sport returns an honest empty response", async () => {
  const gateway = createGateway({ fetcher: async () => { throw new Error("should not fetch"); }, now: () => 1_000 });
  const body = await (await gateway(new Request("https://example.test/v1/sports/events?sport=netball"))).json();
  assert.equal(body.status, "unsupported");
  assert.deepEqual(body.events, []);
  assert.equal(body.diagnostics.unsupportedSport, "netball");
});
