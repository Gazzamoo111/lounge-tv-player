import { test } from "node:test";
import assert from "node:assert/strict";
import {
  category,
  decodeText,
  media,
  nowProgramme,
  programmes,
  nzDay,
  selectEvents,
  Media,
  SportEvent,
} from "../src/model.ts";
import { LibraryState } from "../src/storage.ts";
import { nearest } from "../src/focus.ts";
import { matchTrending, assembleTrending } from "../src/discovery.ts";
import { LoungeClient } from "../src/api.ts";
const item: Media = {
  id: "1",
  kind: "movie",
  name: "The Matrix",
  image: "https://example.com/poster.jpg",
  category: "movie_1",
  ext: "mp4",
  rating: 0,
};
function library() {
  const data = new Map<string, string>();
  return new LibraryState({
    getItem: (k) => data.get(k) ?? null,
    setItem: (k, v) => {
      data.set(k, v);
    },
    removeItem: (k) => {
      data.delete(k);
    },
  });
}
const event = (id: string, start: number, state = "scheduled"): SportEvent => ({
  id,
  externalId: id,
  sport: "basketball",
  feedId: "basketball/nba",
  leagueName: "NBA",
  title: id,
  startUtcMs: start,
  state,
  statusText: "",
  competitors: [],
  broadcasts: [],
});
test("canonical category labels retain backend identity and order", () => {
  const rows = [
    { category_id: "20", category_name: "Sports · UK Sports" },
    { category_id: "3", category_name: "New Zealand · Sports" },
  ].map(category);
  assert.deepEqual(
    rows.map((c) => c.id),
    ["20", "3"],
  );
  assert.equal(rows[0].section, "Sports");
  assert.equal(rows[1].label, "Sports");
});
test("catalogue does not replace gateway IDs with provider IDs or external direct_source", () => {
  const m = media(
    {
      stream_id: 1000001,
      provider_item_id: 999,
      name: "NF The Matrix",
      direct_source: "http://provider.invalid/secret",
    },
    "movie",
  );
  assert.equal(m.id, "1000001");
  assert.equal(m.name, "The Matrix");
  assert.ok(!JSON.stringify(m).includes("provider.invalid"));
});
test("EPG uses absolute epoch time, decodes Unicode and rejects invalid windows", () => {
  const text = "Whakaata Māori";
  const rows = programmes([
    {
      id: 1,
      title: Buffer.from(text).toString("base64"),
      description: "",
      start_timestamp: "100",
      stop_timestamp: "200",
    },
    { id: 2, start_timestamp: "oops", stop_timestamp: "300" },
    { id: 3, start_timestamp: "300", stop_timestamp: "200" },
  ]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].title, text);
  assert.equal(nowProgramme(rows, 150000)?.id, "1");
  assert.equal(nowProgramme(rows, 200000), undefined);
  assert.equal(decodeText("Sky Sport 1"), "Sky Sport 1");
});
test("NZ sports day crosses UTC midnight correctly", () => {
  const start = Date.parse("2026-11-06T12:00:00Z");
  const now = Date.parse("2026-11-06T10:45:00Z");
  assert.notEqual(nzDay(start), nzDay(now));
  assert.equal(
    selectEvents([event("1", start)], "today", "all", now).length,
    0,
  );
});
test("finished and cancelled sports do not become live recommendations", () => {
  const now = Date.now();
  assert.deepEqual(
    selectEvents(
      [
        event("cancel", now, "cancelled"),
        event("finished", now, "finished"),
        event("live", now, "live"),
      ],
      "live",
      "all",
      now,
    ).map((x) => x.id),
    ["live"],
  );
});
test("profiles and customer accounts cannot share Saved or progress", () => {
  const s = library();
  s.scope("alice");
  s.toggle(item);
  s.updateProgress(item, 120, 7200);
  s.addProfile("Kids");
  assert.equal(s.favourites().length, 0);
  assert.equal(s.continuing().length, 0);
  s.scope("bob");
  assert.equal(s.favourites().length, 0);
  s.scope("alice");
  s.switchProfile("default");
  assert.equal(s.favourites().length, 1);
  assert.equal(s.continuing()[0].position, 120);
});
test("95 percent completion removes an item from Keep watching but keeps progress", () => {
  const s = library();
  s.scope("alice");
  s.updateProgress(item, 960, 1000);
  assert.equal(s.continuing().length, 0);
  assert.equal(s.progressFor(item)?.complete, true);
});
test("saving the same media twice toggles without duplicates and keeps types separate", () => {
  const s = library();
  s.scope("a");
  s.toggle(item);
  s.toggle({ ...item, kind: "series" });
  assert.equal(s.favourites().length, 2);
  s.toggle(item);
  assert.deepEqual(
    s.favourites().map((m) => m.kind),
    ["series"],
  );
});
test("spatial focus favours aligned targets and never moves backwards", () => {
  const rect = { left: 0, top: 0, width: 50, height: 50 };
  assert.equal(
    nearest(
      rect,
      [
        { id: "aligned", rect: { ...rect, top: 70 } },
        { id: "diagonal", rect: { ...rect, top: 60, left: 80 } },
        { id: "above", rect: { ...rect, top: -70 } },
      ],
      "down",
    ),
    "aligned",
  );
});
test("trending exact matching rejects remakes with incompatible years and ambiguous titles", () => {
  const c = {
    id: 10,
    title: "The Matrix",
    release_date: "1999-01-01",
    vote_average: 8.2,
    overview: "Test",
  };
  assert.equal(matchTrending([{ ...item, year: "1999" }], [c]).length, 1);
  assert.equal(matchTrending([{ ...item, year: "2020" }], [c]).length, 0);
  assert.equal(matchTrending([item, { ...item, id: "2" }], [c]).length, 0);
  assert.equal(
    matchTrending([{ ...item, name: "The Matrix Reloaded" }], [c]).length,
    0,
  );
});
test("trending assembly follows Android movie/series interleave and ten-item bound", () => {
  const movies = Array.from({ length: 10 }, (_, i) => ({
      ...item,
      id: "m" + i,
    })),
    series = Array.from({ length: 4 }, (_, i) => ({
      ...item,
      id: "s" + i,
      kind: "series" as const,
    }));
  const out = assembleTrending(movies, series);
  assert.equal(out.length, 10);
  assert.deepEqual(
    out.slice(0, 4).map((m) => m.id),
    ["m0", "s0", "m1", "s1"],
  );
});
test("playback routes use only customer gateway and encode credentials", () => {
  const client = new LoungeClient({ username: "a/b", password: "p?&" });
  const url = client.playback(item);
  assert.equal(url, "https://connect.loungetv.nz/movie/a%2Fb/p%3F%26/1.mp4");
  assert.match(
    client.playback({ ...item, kind: "episode", ext: "mkv" }),
    /\/series\//,
  );
  assert.match(
    client.sportPlayback({ ...item, kind: "live" }),
    /lounge-core-xtream\/sport\/_\/_\/1.ts/,
  );
});
