import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { HERO_ART, heroAsset } from "../src/hero-art.ts";
import { DISCOVERY_FEEDS } from "../src/sports.ts";
test("every primary discovery sport and known supplementary sport maps to real packaged WebP artwork", () => {
  for (const sport of new Set([
    ...DISCOVERY_FEEDS.map((f) => f.split("/")[0]),
    ...Object.keys(HERO_ART),
  ])) {
    const path = heroAsset(sport);
    assert.ok(path, sport);
    const bytes = readFileSync("public/" + path);
    assert.equal(bytes.toString("ascii", 0, 4), "RIFF");
    assert.equal(bytes.toString("ascii", 8, 12), "WEBP");
    assert.ok(bytes.length > 10000);
  }
  assert.notEqual(heroAsset("golf"), heroAsset("boxing"));
  assert.equal(heroAsset("unknown"), undefined);
});
