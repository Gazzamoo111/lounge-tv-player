import { test } from "node:test";
import assert from "node:assert/strict";
import { GuideMatcher, channelRegion } from "../src/guide-match.ts";
import { hasCurrentGuide, mergeGuide } from "../src/guide-policy.ts";
import { LoungeClient } from "../src/api.ts";
const now = Date.now();
const p = (id: string, start: number, end: number, title = id) => ({
  id,
  title,
  description: "",
  start: now + start,
  end: now + end,
});
test("future-only guide data does not count as a programme on now", () => {
  assert.equal(hasCurrentGuide([p("later", 60000, 120000)], now), false);
  assert.equal(hasCurrentGuide([p("old", -120000, -60000)], now), false);
  assert.equal(hasCurrentGuide([p("now", -60000, 60000)], now), true);
  assert.equal(hasCurrentGuide([p("blank", -60000, 60000, "")], now), false);
});
test("fallback fills real gaps while canonical overlapping intervals remain authoritative", () => {
  const result = mergeGuide(
    [p("primary", -60000, 60000)],
    [p("wrong", -30000, 30000), p("next", 60000, 120000), p("invalid", 5, 2)],
    now,
  );
  assert.deepEqual(
    result.map((r) => r.id),
    ["primary", "next"],
  );
});
test("different streams sharing an upstream EPG ID keep distinct device cache identities", () => {
  const matcher = new GuideMatcher([
    { id: "a", name: "A", epgId: "shared.au", cacheKey: "stream:a" },
    { id: "b", name: "B", epgId: "shared.au", cacheKey: "stream:b" },
  ]);
  assert.deepEqual(matcher.targets("shared.au"), ["stream:a", "stream:b"]);
});
test("Australia Ten uses the main Ten schedule instead of the incorrectly supplied 10 Bold ID", () => {
  const matcher = new GuideMatcher([
    {
      id: "ten",
      name: "Ten",
      epgId: "10Bold.au",
      region: "au",
      cacheKey: "stream:ten",
    },
    {
      id: "bold",
      name: "10 BOLD",
      epgId: "10Bold.au",
      region: "au",
      cacheKey: "stream:bold",
    },
  ]);
  assert.deepEqual(matcher.targets("10Bold.au"), ["stream:bold"]);
  assert.deepEqual(matcher.targets("mjh-10-nsw"), ["stream:ten"]);
  assert.deepEqual(matcher.targets("mjh-10bold-nsw"), ["stream:bold"]);
});
test("regional fallback aliases are not applied to a similarly named overseas channel", () => {
  const matcher = new GuideMatcher([
    {
      id: "us",
      name: "Channel 9",
      epgId: "NBC.us",
      region: "us",
      cacheKey: "stream:us",
    },
  ]);
  assert.deepEqual(matcher.targets("mjh-channel-9-nsw"), []);
  assert.equal(
    channelRegion({ name: "Bravo", epgId: "Bravo.us" }, "Entertainment · US"),
    "us",
  );
  assert.equal(
    channelRegion({ name: "Ten", epgId: "10Bold.au" }, "Australia · TV"),
    "au",
  );
});
test("explicit user EPG match wins over the built-in regional alias", () => {
  const matcher = new GuideMatcher(
    [{ id: "9", name: "Channel 9", region: "au", cacheKey: "stream:9" }],
    { "9": "custom-nine" },
  );
  assert.deepEqual(matcher.targets("mjh-channel-9-nsw"), []);
  assert.deepEqual(matcher.targets("custom-nine"), ["stream:9"]);
});
test("short guide with only a future listing still fetches the current gateway programme", async () => {
  const original = globalThis.fetch,
    requests: string[] = [];
  globalThis.fetch = async (url: any) => {
    requests.push(String(url));
    return new Response(
      JSON.stringify({
        epg_listings: String(url).includes("raw.githubusercontent")
          ? [
              {
                id: 1,
                title: "Later",
                start_timestamp: (now + 60000) / 1000,
                stop_timestamp: (now + 120000) / 1000,
              },
            ]
          : [
              {
                id: 2,
                title: "Current show",
                start_timestamp: (now - 60000) / 1000,
                stop_timestamp: (now + 60000) / 1000,
              },
            ],
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  };
  try {
    const rows = await new LoungeClient({
      username: "fixture",
      password: "fixture",
    }).guide("1", "test.nz");
    assert.equal(requests.length, 2);
    assert.equal(hasCurrentGuide(rows, now), true);
    assert.equal(rows.length, 2);
  } finally {
    globalThis.fetch = original;
  }
});

test("identical quality variants with the same regional EPG identity receive the same exact-name match", () => {
  const matcher = new GuideMatcher([
    {
      id: "a",
      name: "Sky Sports Golf HD",
      epgId: "SkySportsGolf.uk",
      region: "uk",
      cacheKey: "stream:a",
    },
    {
      id: "b",
      name: "Sky Sports Golf 4K",
      epgId: "SkySportsGolf.uk",
      region: "uk",
      cacheKey: "stream:b",
    },
  ]);
  matcher.register("another-golf-id", ["Sky Sports Golf"]);
  assert.deepEqual(matcher.targets("another-golf-id"), [
    "stream:a",
    "stream:b",
  ]);
});
