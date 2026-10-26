import assert from "node:assert/strict";
import test from "node:test";
import { collectEvents, dedupe, normalizeEspnEvent, parseQuery } from "../src/index.mjs";

test("query rejects windows longer than 72 hours and unknown sports", () => {
  assert.throws(() => parseQuery(new URL("https://example.test/v1/sports/events?from=0&to=9999999999999"), 1_000), RangeError);
  assert.throws(() => parseQuery(new URL("https://example.test/v1/sports/events?sport=netball"), 1_000), RangeError);
});

test("normalizer creates stable provider id and never a watchability claim", () => {
  const event = normalizeEspnEvent({
    id: "42",
    date: "2026-10-26T00:20:00Z",
    status: { type: { state: "in", shortDetail: "Q2" } },
    competitions: [{ competitors: [] }],
  }, { sport: "nfl", path: "football", league: "nfl" }, 5_000);
  assert.equal(event.id, "espn:football/nfl#42");
  assert.equal(event.state, "live");
  assert.equal("watchable" in event, false);
  assert.equal("channelId" in event, false);
});

test("dedupe rejects out-of-order scheduled regression", () => {
  const base = { id: "espn:x#1", startUtcMs: 2, sourceUpdatedAtMs: 2 };
  assert.equal(dedupe([{ ...base, state: "finished" }, { ...base, state: "scheduled", sourceUpdatedAtMs: 3 }])[0].state, "finished");
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
