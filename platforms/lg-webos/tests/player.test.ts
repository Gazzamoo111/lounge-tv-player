import { test } from "node:test";
import assert from "node:assert/strict";
import { LgPlayback } from "../src/player.ts";
import { Media } from "../src/model.ts";
class Video extends EventTarget {
  src = "";
  currentTime = 0;
  duration = 200;
  paused = true;
  error: { code: number } | null = null;
  textTracks: any[] = [
    { label: "English", mode: "disabled" },
    { label: "Māori", mode: "disabled" },
  ];
  audioTracks = [
    { language: "en", enabled: true },
    { language: "mi", enabled: false },
  ];
  loads = 0;
  playTask: Promise<void> = Promise.resolve();
  load() {
    this.loads++;
  }
  async play() {
    await this.playTask;
    this.paused = false;
    this.dispatchEvent(new Event("playing"));
  }
  pause() {
    this.paused = true;
    this.dispatchEvent(new Event("pause"));
  }
  removeAttribute() {
    this.src = "";
  }
}
const m: Media = {
  id: "1",
  kind: "movie",
  name: "Test",
  image: "",
  category: "1",
  ext: "mp4",
  rating: 0,
};
test("native metadata resumes VOD and valid seek remains within duration", async () => {
  const v = new Video(),
    p = new LgPlayback(v as any);
  await p.load(m, "https://connect.loungetv.nz/movie/test/redacted/1.mp4", 85);
  v.dispatchEvent(new Event("loadedmetadata"));
  assert.equal(v.currentTime, 85);
  p.seek(500);
  assert.equal(v.currentTime, 199);
  p.stop();
});
test("superseded play rejection cannot change the new stream state", async () => {
  const v = new Video(),
    p = new LgPlayback(v as any);
  let reject!: (e: Error) => void;
  v.playTask = new Promise((_, r) => (reject = r));
  const old = p.load(
    m,
    "https://connect.loungetv.nz/movie/test/redacted/1.mp4",
  );
  v.playTask = Promise.resolve();
  await p.load(
    { ...m, id: "2" },
    "https://connect.loungetv.nz/movie/test/redacted/2.mp4",
  );
  reject(Error("superseded"));
  await old;
  assert.equal(p.media?.id, "2");
  assert.equal(p.state, "playing");
  p.stop();
});
test("only one native stream remains after channel switching and stop detaches callbacks", async () => {
  const v = new Video(),
    p = new LgPlayback(v as any);
  await p.load(
    { ...m, kind: "live" },
    "https://connect.loungetv.nz/live/test/redacted/1.ts",
  );
  await p.load(
    { ...m, id: "2", kind: "live" },
    "https://connect.loungetv.nz/live/test/redacted/2.ts",
  );
  p.seek(20);
  assert.equal(v.currentTime, 0);
  p.stop();
  v.dispatchEvent(new Event("error"));
  assert.equal(p.state, "idle");
  assert.equal(v.src, "");
  assert.equal(p.media, undefined);
});
test("native audio and subtitle selection is exclusive and subtitles can be disabled", () => {
  const v = new Video(),
    p = new LgPlayback(v as any);
  p.selectTrack("audio", 1);
  assert.deepEqual(
    v.audioTracks.map((t) => t.enabled),
    [false, true],
  );
  p.selectTrack("subtitle", 1);
  assert.deepEqual(
    v.textTracks.map((t) => t.mode),
    ["disabled", "showing"],
  );
  p.selectTrack("subtitle", -1);
  assert.ok(v.textTracks.every((t) => t.mode === "disabled"));
  assert.equal(p.tracks("audio")[1].selected, true);
});
test("diagnostics never contain URLs, account credentials or catalogue payload", async () => {
  const v = new Video(),
    p = new LgPlayback(v as any);
  await p.load(
    m,
    "https://connect.loungetv.nz/movie/private-user/private-password/1.mp4",
  );
  v.error = { code: 4 };
  v.dispatchEvent(new Event("error"));
  p.stop();
  const trace = JSON.stringify(p.trace);
  assert.ok(!/https|private-user|private-password/.test(trace));
  assert.ok(p.trace.some((t) => t.event === "native-error" && t.code === 4));
});
