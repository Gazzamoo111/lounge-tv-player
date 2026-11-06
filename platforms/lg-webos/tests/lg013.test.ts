import { test } from "node:test";
import assert from "node:assert/strict";
import { GuideMatcher, guideHash } from "../src/guide-match.ts";
import {
  parseScoreboard,
  discoverSports,
  DISCOVERY_FEEDS,
  mergeFixtures,
} from "../src/sports.ts";
import { artworkUrl } from "../src/artwork.ts";
import { LibraryState } from "../src/storage.ts";
import { nzDay } from "../src/model.ts";
import { XmltvStream } from "../src/xmltv.ts";
const now = Date.parse("2026-11-06T11:30:00Z");
function game(id = "1", sport = "basketball") {
  return {
    leagues: [{ name: sport === "basketball" ? "NBA" : "League" }],
    events: [
      {
        id,
        name: "Home v Away",
        date: new Date(now + 3600000).toISOString(),
        competitions: [
          {
            id,
            date: new Date(now + 3600000).toISOString(),
            status: { type: { state: "pre" } },
            competitors: [
              { homeAway: "home", team: { id: "1", displayName: "Home" } },
              { homeAway: "away", team: { id: "2", displayName: "Away" } },
            ],
            broadcasts: [{ names: ["ESPN"] }],
          },
        ],
      },
    ],
  };
}
test("guide matching accepts declared IDs and canonical stream aliases, and refuses ambiguous names", () => {
  const m = new GuideMatcher([
    { id: "1", epgId: "sky.nz", name: "Sky Sport 1 HD" },
    { id: "2", epgId: "abc.au", name: "ABC" },
    { id: "3", epgId: "abc.us", name: "ABC" },
  ]);
  assert.deepEqual(m.targets("sky.nz"), ["sky.nz"]);
  assert.deepEqual(m.targets("ltv-1"), ["sky.nz"]);
  m.register("SkySport1.nz", ["Sky Sport 1"]);
  assert.deepEqual(m.targets("SkySport1.nz"), ["sky.nz"]);
  m.register("unknown", ["ABC"]);
  assert.deepEqual(m.targets("unknown"), []);
});
test("explicit EPG override replaces the old match without rewriting the catalogue ID", () => {
  const m = new GuideMatcher(
    [{ id: "1", epgId: "old.nz", name: "Sky Sport 1" }],
    { "1": "correct.nz" },
  );
  assert.deepEqual(m.targets("old.nz"), []);
  assert.deepEqual(m.targets("correct.nz"), ["old.nz"]);
});
test("guide hash uses the same canonical UTF-8 FNV identity as the existing short-guide endpoint", () => {
  assert.equal(guideHash("SkySport1.nz"), "a03d516b");
});
test("channel display-name declarations split across chunks do not swallow later programmes", () => {
  const parser = new XmltvStream(),
    channels: string[] = [],
    programmes: string[] = [];
  const xml =
    '<tv><channel id="a"><display-name>Sky Sport 1</display-name></channel><channel id="b"/><programme channel="a">x</programme></tv>';
  for (let i = 0; i < xml.length; i += 3)
    parser.feed(
      xml.slice(i, i + 3),
      (s) => programmes.push(s),
      (s) => channels.push(s),
    );
  assert.equal(channels.length, 2);
  assert.equal(programmes.length, 1);
});
test("TMDB image decoding is bounded without changing non-TMDB artwork", () => {
  assert.equal(
    artworkUrl("https://image.tmdb.org/t/p/original/a.jpg"),
    "https://image.tmdb.org/t/p/w342/a.jpg",
  );
  assert.equal(
    artworkUrl("https://image.tmdb.org/t/p/original/a.jpg", true),
    "https://image.tmdb.org/t/p/w780/a.jpg",
  );
  assert.equal(
    artworkUrl("https://other.test/original/a.jpg"),
    "https://other.test/original/a.jpg",
  );
});
test("sports discovery uses seven NZ calendar dates and every Android discovery feed with bounded concurrency", async () => {
  let active = 0,
    max = 0;
  const urls: string[] = [];
  const d = await discoverSports(async (u) => {
    urls.push(u);
    active++;
    max = Math.max(max, active);
    await Promise.resolve();
    active--;
    return u.includes("basketball/nba") ? game() : { events: [] };
  }, now);
  assert.ok(max <= 4);
  assert.equal(DISCOVERY_FEEDS.length, 31);
  assert.equal(urls.filter((u) => u.includes("sport=cricket")).length, 7);
  assert.ok(urls.some((u) => u.includes("dates=20261107")));
  assert.ok(urls.some((u) => u.includes("/rugby-league/3/")));
  assert.equal(d.events.length, 1);
  assert.equal(d.events[0].leagueName, "NBA");
  assert.equal(d.events[0].broadcasts[0].name, "ESPN");
  assert.equal(nzDay(now), "2026-11-07");
});
test("one failed fixture feed preserves events from healthy feeds", async () => {
  const d = await discoverSports(async (u) => {
    if (u.includes("/soccer/")) throw Error("offline");
    return u.includes("basketball/nba") ? game() : { events: [] };
  }, now);
  assert.equal(d.failed, 3);
  assert.equal(d.events.length, 1);
});
test("tennis competitions, F1 sessions and MMA cards retain distinct fixture identities", () => {
  const e = game().events[0],
    c = e.competitions[0];
  const tennis = parseScoreboard("tennis/atp", {
    events: [
      {
        ...e,
        groupings: [
          {
            competitions: [
              { ...c, id: "a" },
              { ...c, id: "b" },
            ],
          },
        ],
      },
    ],
  });
  assert.equal(tennis.length, 2);
  assert.notEqual(tennis[0].id, tennis[1].id);
  const f1 = parseScoreboard("racing/f1", {
    events: [
      {
        ...e,
        competitions: [
          c,
          { ...c, date: new Date(now + 86400000).toISOString() },
        ],
      },
    ],
  });
  assert.equal(f1.length, 2);
  assert.notEqual(f1[0].id, f1[1].id);
  const mma = parseScoreboard("mma/ufc", {
    events: [
      { ...e, competitions: [{ ...c, status: { type: { state: "in" } } }, c] },
    ],
  });
  assert.equal(mma.length, 1);
  assert.equal(mma[0].state, "live");
});
test("sports merge deduplicates an exact fixture across feeds while preserving editorial artwork", () => {
  const e = parseScoreboard("basketball/nba", game())[0];
  const merged = mergeFixtures(
    [e, { ...e, id: "calendar:1", hero_art_url: "assets/nba.webp" }],
    now,
  );
  assert.equal(merged.length, 1);
  assert.equal(merged[0].hero_art_url, "assets/nba.webp");
  assert.equal(
    mergeFixtures(
      [e, { ...e, id: "different", startUtcMs: e.startUtcMs + 86400000 }],
      now,
    ).length,
    2,
  );
});
test("existing saved settings gain responsive defaults without discarding user preferences", () => {
  const data = new Map<string, string>(),
    s = new LibraryState({
      getItem: (k) => data.get(k) ?? null,
      setItem: (k, v) => {
        data.set(k, v);
      },
      removeItem: (k) => {
        data.delete(k);
      },
    });
  s.scope("test");
  s.write("settings", { autoplayNext: false, aspect: "cover" });
  assert.equal(s.settings().autoplayNext, false);
  assert.equal(s.settings().aspect, "cover");
  assert.equal(s.settings().metadataOnFocus, false);
  assert.equal(s.settings().vodColumns, 6);
  s.savePlaylist({ username: "test", password: "example" }, "Main");
  s.savePlaylist({ username: "test", password: "changed" });
  assert.equal(s.playlists().length, 1);
  assert.equal(s.playlists()[0].name, "Main");
});
