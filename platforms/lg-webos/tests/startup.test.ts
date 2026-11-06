import { test } from "node:test";
import assert from "node:assert/strict";
import { mapLimit } from "../src/tasks.ts";
import { xmltvTime, programmeFromXml, XmltvStream } from "../src/xmltv.ts";
import { categoryGroups } from "../src/categories.ts";
import { category } from "../src/model.ts";
import { completeCatalogue } from "../src/cache.ts";
test("bounded work never exceeds the permitted concurrency and retains input order", async () => {
  let active = 0,
    peak = 0;
  const rows = await mapLimit([1, 2, 3, 4, 5, 6], 2, async (n) => {
    active++;
    peak = Math.max(peak, active);
    await new Promise((r) => setTimeout(r, n % 2 ? 4 : 1));
    active--;
    return n * 2;
  });
  assert.equal(peak, 2);
  assert.deepEqual(rows, [2, 4, 6, 8, 10, 12]);
});
test("XMLTV explicit timezone offsets are absolute, including NZ daylight time", () => {
  assert.equal(
    xmltvTime("20261106210000 +1300"),
    Date.parse("2026-11-06T08:00:00Z"),
  );
  assert.equal(
    xmltvTime("20261106130000 -0500"),
    Date.parse("2026-11-06T18:00:00Z"),
  );
  assert.ok(Number.isNaN(xmltvTime("not a timestamp")));
});
test("streaming XMLTV records may be split at every tag boundary without losing programme identity", () => {
  const parser = new XmltvStream(),
    out: string[] = [];
  const text = `<?xml version="1.0"?><tv><channel id="sky"/><programme channel="sky&amp;1" start="20261106200000 +1300" stop="20261106210000 +1300"><title>Rugby &amp; Cricket</title><desc><![CDATA[Māori & sport]]></desc></programme></tv>`;
  for (let i = 0; i < text.length; i += 7)
    parser.feed(text.slice(i, i + 7), (v) => out.push(v));
  assert.equal(out.length, 1);
  const row = programmeFromXml(out[0])!;
  assert.equal(row.channel, "sky&1");
  assert.equal(row.programme.title, "Rugby & Cricket");
  assert.equal(row.programme.description, "Māori & sport");
  assert.equal(row.programme.start, Date.parse("2026-11-06T07:00:00Z"));
});
test("malformed guide entries do not invent titles or times", () => {
  assert.equal(
    programmeFromXml(
      '<programme channel="1" start="bad" stop="bad"><title>Test</title></programme>',
    ),
    undefined,
  );
  assert.equal(
    programmeFromXml(
      '<programme channel="1" start="20261106210000 +1300" stop="20261106200000 +1300"></programme>',
    ),
    undefined,
  );
});
test("category accordion retains every category ID, including empty and interleaved folders", () => {
  const input = [
    { category_id: "nz-tv", category_name: "New Zealand · TV" },
    { category_id: "au-sport", category_name: "Australia · Sport" },
    { category_id: "nz-sport", category_name: "New Zealand · Sport" },
    { category_id: "empty", category_name: "Sports · Empty folder" },
  ].map(category);
  const groups = categoryGroups(input);
  assert.deepEqual(
    groups.map((x) => x.name),
    ["New Zealand", "Australia", "Sports"],
  );
  assert.deepEqual(
    groups[0].items.map((x) => x.id),
    ["nz-tv", "nz-sport"],
  );
  assert.equal(groups.flatMap((g) => g.items).length, input.length);
});
test("partial catalogue cache is never accepted as a complete startup", () => {
  assert.equal(completeCatalogue(undefined), false);
  assert.equal(
    completeCatalogue({
      at: Date.now(),
      categories: { live: [] },
      catalogues: { live: [] },
    }),
    false,
  );
  assert.equal(
    completeCatalogue({
      at: Date.now(),
      categories: { live: [], movie: [], series: [] },
      catalogues: { live: [], movie: [], series: [] },
    }),
    true,
  );
});
