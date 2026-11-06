import { test } from "node:test";
import assert from "node:assert/strict";
import { freeviewSource } from "../src/freeview.ts";
import type { Media } from "../src/model.ts";
const base: Media = {
  id: "900000001",
  kind: "live",
  name: "TVNZ 1",
  image: "",
  category: "nz",
  ext: "ts",
  rating: 0,
};
test("TVNZ reserves direct DASH and normal Widevine licenses instead of synthetic provider stream IDs", () => {
  assert.deepEqual(freeviewSource(base), {
    url: "https://i.mjh.nz/.r/tvnz-1.mpd",
    manifest: "dash",
    license: "https://c.mjh.nz/tvnz-1-wv",
  });
  assert.equal(
    freeviewSource({ ...base, name: "TVNZ 2" })?.license,
    "https://c.mjh.nz/tvnz-2-wv",
  );
});
test("Freeview HLS routing requires exact NZ identities for ambiguous channel names", () => {
  for (const [epgId, slug] of [
    ["mjh-three", "three"],
    ["mjh-maori-tv", "maori-tv"],
    ["mjh-te-reo", "te-reo"],
  ]) {
    assert.deepEqual(
      freeviewSource({ ...base, name: "Regional channel", epgId }),
      { url: `https://i.mjh.nz/.r/${slug}.m3u8`, manifest: "hls" },
    );
  }
  assert.equal(freeviewSource({ ...base, name: "Three" }), undefined);
  assert.equal(
    freeviewSource({ ...base, name: "TVNZ 1", kind: "movie" }),
    undefined,
  );
  assert.equal(freeviewSource({ ...base, name: "SKY SPORT 1" }), undefined);
});
