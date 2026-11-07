import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { teamCrest } from "../src/team-crests.ts";
import { mergeFixtures, parseScoreboard } from "../src/sports.ts";
import { SportEvent } from "../src/model.ts";
const fixtures: SportEvent[] = JSON.parse(
  readFileSync(
    new URL("./fixtures/rlwc-calendar.json", import.meta.url),
    "utf8",
  ),
);
test("actual null-crest RLWC calendar fixtures resolve correct bundled men's and women's nation crests", () => {
  for (const fixture of fixtures)
    for (const team of fixture.competitors) {
      assert.equal(team.crestUrl, null);
      const path = teamCrest(fixture, team);
      assert.match(path, /^assets\/teams\//);
      assert.ok(
        readFileSync(new URL("../public/" + path, import.meta.url)).length >
          100,
      );
    }
  const women = fixtures.find((e) => e.subtitle?.includes("Women"))!;
  assert.equal(
    teamCrest(women, women.competitors.find((t) => t.name === "New Zealand")!),
    "assets/teams/new-zealand-women.png",
  );
  const men = fixtures.find((e) => e.title === "New Zealand vs Tonga")!;
  assert.equal(
    teamCrest(men, men.competitors[0]),
    "assets/teams/new-zealand.svg",
  );
  const unrelated = { ...men, sport: "rugby", feedId: "rugby/1" };
  assert.equal(teamCrest(unrelated, unrelated.competitors[0]), "");
});
test("calendar metadata cannot erase discovered crests, even when competitor order and IDs differ", () => {
  const base = fixtures[0];
  const discovered = {
    ...base,
    competitors: base.competitors.map((t, i) => ({
      ...t,
      id: String(i),
      crestUrl: "https://images.test/" + i + ".png",
    })),
  };
  const calendar = { ...base, competitors: [...base.competitors].reverse() };
  for (const rows of [
    [discovered, calendar],
    [calendar, discovered],
  ]) {
    const merged = mergeFixtures(rows, base.startUtcMs - 1000);
    assert.equal(merged.length, 1);
    for (const team of merged[0].competitors)
      assert.equal(
        team.crestUrl,
        discovered.competitors.find((t) => t.name === team.name)!.crestUrl,
      );
  }
});
test("scoreboard logo arrays are parsed as well as the single-logo form", () => {
  const events = parseScoreboard("hockey/nhl", {
    events: [
      {
        id: "game",
        name: "Home v Away",
        date: new Date().toISOString(),
        competitions: [
          {
            competitors: [
              {
                team: {
                  id: "h",
                  displayName: "Home",
                  logos: [{ href: "https://images.test/home.png" }],
                },
              },
              {
                team: {
                  id: "a",
                  displayName: "Away",
                  logo: "https://images.test/away.png",
                },
              },
            ],
          },
        ],
      },
    ],
  });
  assert.deepEqual(
    events[0].competitors.map((t) => t.crestUrl),
    ["https://images.test/home.png", "https://images.test/away.png"],
  );
});
