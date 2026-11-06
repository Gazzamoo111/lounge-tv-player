import { test } from "node:test";
import assert from "node:assert/strict";
import { LgPlayback } from "../src/player.ts";
import { Media } from "../src/model.ts";
class Video extends EventTarget {
  private testSource = "";
  get src() {
    return this.testSource;
  }
  set src(value: string) {
    this.testSource = value;
  }
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

class Clock {
  time = 0;
  visible = true;
  next = 1;
  timers = new Map<number, { at: number; every: number; fn: () => void }>();
  api = {
    now: () => this.time,
    foreground: () => this.visible,
    timeout: (fn: () => void, ms: number) => this.add(fn, ms, 0) as any,
    cancelTimeout: (id: any) => {
      this.timers.delete(id);
    },
    interval: (fn: () => void, ms: number) => this.add(fn, ms, ms) as any,
    cancelInterval: (id: any) => {
      this.timers.delete(id);
    },
  };
  add(fn: () => void, ms: number, every: number) {
    const id = this.next++;
    this.timers.set(id, { at: this.time + ms, every, fn });
    return id;
  }
  async advance(ms: number) {
    const target = this.time + ms;
    let steps = 0;
    while (true) {
      const entries = [...this.timers]
        .filter(([, t]) => t.at <= target)
        .sort((a, b) => a[1].at - b[1].at);
      if (!entries.length) break;
      if (++steps > 10000) throw Error("Runaway retry loop");
      const [id, t] = entries[0];
      this.time = t.at;
      if (t.every) t.at += t.every;
      else this.timers.delete(id);
      t.fn();
      await Promise.resolve();
      await Promise.resolve();
    }
    this.time = target;
    await Promise.resolve();
    await Promise.resolve();
  }
}
class LiveVideo extends Video {
  ended = false;
  readyState = 4;
  networkState = 2;
  frames = 0;
  connections: string[] = [];
  private source = "";
  get src() {
    return this.source;
  }
  set src(value: string) {
    this.source = value;
    if (value) this.connections.push(value);
  }
  getVideoPlaybackQuality() {
    return { totalVideoFrames: this.frames };
  }
  load() {
    this.loads++;
    this.currentTime = 0;
    this.ended = false;
  }
  removeAttribute() {
    this.source = "";
  }
}
async function advanceLive(clock: Clock, v: LiveVideo, seconds: number) {
  for (let i = 0; i < seconds; i++) {
    if (!v.paused) {
      v.currentTime++;
      v.frames += 25;
      v.dispatchEvent(new Event("timeupdate"));
    }
    await clock.advance(1000);
  }
}
const liveMedia = { ...m, kind: "live" as const, ext: "ts" };
const liveUrl = "https://connect.loungetv.nz/live/test/redacted/1.ts";
test("a live native EOF after 150 seconds reconnects instead of ending the viewing session", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  let ended = 0;
  p.onEnded = () => ended++;
  await p.load(liveMedia, liveUrl);
  await advanceLive(clock, v, 150);
  v.ended = true;
  v.dispatchEvent(new Event("ended"));
  assert.equal(p.state, "buffering");
  assert.equal(v.src, "");
  await clock.advance(1000);
  assert.equal(p.state, "playing");
  assert.equal(ended, 0);
  assert.equal(v.connections.length, 2);
  assert.ok(v.connections[1].includes("_ltv_restart="));
  assert.ok(
    p.trace.some((t) => t.event === "live-eof" && t.elapsed! >= 150000),
  );
  p.stop();
});
test("healthy viewing resets outage budget, allowing repeated 2.5-minute live segments for over 20 minutes", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  await p.load(liveMedia, liveUrl);
  for (let i = 0; i < 9; i++) {
    await advanceLive(clock, v, 150);
    v.ended = true;
    v.dispatchEvent(new Event("ended"));
    await clock.advance(1000);
    assert.equal(p.state, "playing");
  }
  assert.ok(clock.time > 20 * 60000);
  assert.equal(v.connections.length, 10);
  assert.ok(p.trace.some((t) => t.event === "recovery-budget-reset"));
  p.stop();
});
test("a live transport clock moving backwards does not trip the watchdog", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  await p.load(liveMedia, liveUrl);
  await advanceLive(clock, v, 150);
  v.currentTime = 0;
  v.frames++;
  v.dispatchEvent(new Event("timeupdate"));
  await advanceLive(clock, v, 35);
  assert.equal(v.connections.length, 1);
  assert.equal(p.state, "playing");
  assert.ok(p.trace.some((t) => t.event === "clock-discontinuity"));
  p.stop();
});
test("unexpected native pause resumes the same connection; an intentional pause is respected for minutes", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  await p.load(liveMedia, liveUrl);
  await advanceLive(clock, v, 150);
  v.pause();
  await clock.advance(500);
  assert.equal(p.state, "playing");
  assert.equal(v.connections.length, 1);
  assert.equal(v.paused, false);
  p.pauseByUser();
  await clock.advance(180000);
  assert.equal(p.state, "paused");
  assert.equal(v.paused, true);
  assert.equal(v.connections.length, 1);
  await p.resumeByUser();
  assert.equal(p.state, "playing");
  p.stop();
});
test("the watchdog also polls clock/frames when native timeupdate events disappear", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  await p.load(liveMedia, liveUrl);
  for (let i = 0; i < 180; i++) {
    v.currentTime++;
    v.frames += 25;
    await clock.advance(1000);
  }
  assert.equal(v.connections.length, 1);
  assert.equal(p.state, "playing");
  p.stop();
});
test("continuous failure remains bounded and stopping cancels a scheduled reconnect", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  await p.load(liveMedia, liveUrl);
  await clock.advance(180000);
  assert.equal(p.state, "error");
  assert.equal(v.connections.length, 5);
  const c2 = new Clock(),
    v2 = new LiveVideo(),
    p2 = new LgPlayback(v2 as any, c2.api);
  await p2.load(liveMedia, liveUrl);
  v2.ended = true;
  v2.dispatchEvent(new Event("ended"));
  p2.stop();
  await c2.advance(300000);
  assert.equal(v2.connections.length, 1);
  assert.equal(p2.state, "idle");
  p.stop();
});
test("a completed movie is terminal and never becomes a live reconnect loop", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  let completed = 0;
  p.onEnded = () => completed++;
  await p.load(m, "https://connect.loungetv.nz/movie/test/redacted/1.mp4");
  v.ended = true;
  v.dispatchEvent(new Event("ended"));
  await clock.advance(180000);
  assert.equal(p.state, "ended");
  assert.equal(v.connections.length, 1);
  assert.equal(completed, 1);
  p.stop();
});
test("decoded frame progress prevents false stall detection when the transport clock is unchanged", async () => {
  const clock = new Clock(),
    v = new LiveVideo(),
    p = new LgPlayback(v as any, clock.api);
  await p.load(liveMedia, liveUrl);
  for (let i = 0; i < 150; i++) {
    v.frames += 25;
    await clock.advance(1000);
  }
  assert.equal(v.connections.length, 1);
  assert.equal(p.state, "playing");
  p.stop();
});

test("TVNZ adaptive session receives the direct manifest/license and is destroyed before native switching", async () => {
  const video = new Video();
  const calls: string[] = [];
  let finish!: () => void;
  const cleanup = new Promise<void>((r) => (finish = r));
  const p = new LgPlayback(video as any, undefined, async () => ({
    async attach(v) {
      assert.equal(v, video);
      calls.push("attach");
    },
    async load(url, license) {
      calls.push(url, license);
    },
    async destroy() {
      calls.push("destroy");
      await cleanup;
    },
  }));
  await p.load(
    { ...m, kind: "live" },
    "https://gateway.test/live/900000001.ts",
    0,
    {
      url: "https://source.test/tvnz.mpd",
      manifest: "dash",
      license: "https://license.test/wv",
    },
  );
  assert.deepEqual(calls, [
    "attach",
    "https://source.test/tvnz.mpd",
    "https://license.test/wv",
  ]);
  assert.equal(p.state, "playing");
  const next = p.load(
    { ...m, kind: "live", id: "2" },
    "https://gateway.test/live/2.ts",
  );
  await new Promise((r) => setImmediate(r));
  assert.equal(calls.at(-1), "destroy");
  assert.equal(video.src, "");
  finish();
  await next;
  assert.equal(video.src, "https://gateway.test/live/2.ts");
  assert.equal(p.state, "playing");
  assert.ok(!JSON.stringify(p.trace).includes("license.test"));
  p.stop();
});
test("cancelled delayed adaptive factory never attaches to or overwrites a newer native stream", async () => {
  const video = new Video();
  let ready!: (session: any) => void;
  const pending = new Promise<any>((r) => (ready = r));
  const p = new LgPlayback(video as any, undefined, () => pending);
  let attaches = 0,
    destroys = 0;
  const old = p.load(
    { ...m, kind: "live" },
    "https://gateway.test/live/1.ts",
    0,
    {
      url: "https://source.test/tvnz.mpd",
      manifest: "dash",
      license: "https://license.test/wv",
    },
  );
  await p.load(
    { ...m, kind: "live", id: "2" },
    "https://gateway.test/live/2.ts",
  );
  ready({
    async attach() {
      attaches++;
    },
    async load() {
      assert.fail("stale session must not load");
    },
    async destroy() {
      destroys++;
    },
  });
  await old;
  assert.equal(attaches, 0);
  assert.equal(destroys, 1);
  assert.equal(video.src, "https://gateway.test/live/2.ts");
  assert.equal(p.media?.id, "2");
  assert.equal(p.state, "playing");
  p.stop();
});

test("DRM startup waits for manifest/license preparation without watchdog play on an empty video", async () => {
  const clock = new Clock();
  const video = new LiveVideo();
  let finish!: () => void;
  const waiting = new Promise<void>((r) => (finish = r));
  let destroys = 0;
  const p = new LgPlayback(video as any, clock.api, async () => ({
    async attach() {
      video.dispatchEvent(new Event("pause"));
    },
    async load() {
      await waiting;
    },
    async destroy() {
      destroys++;
    },
  }));
  const loading = p.load(
    { ...m, kind: "live" },
    "https://gateway.test/live/1.ts",
    0,
    {
      url: "https://source.test/live.mpd",
      manifest: "dash",
      license: "https://license.test/wv",
    },
  );
  await new Promise((r) => setImmediate(r));
  await clock.advance(10000);
  assert.equal(p.state, "loading");
  assert.equal(destroys, 0);
  assert.equal(video.paused, true);
  assert.ok(
    !p.trace.some(
      (t) => t.event === "resume-unexpected-pause" || t.event === "reconnect",
    ),
  );
  finish();
  await loading;
  assert.equal(p.state, "playing");
  p.stop();
});

test("NZ HLS adaptive failure falls back to its same native source, without touching provider playback", async () => {
  const clock = new Clock(),
    video = new LiveVideo();
  const p = new LgPlayback(video as any, clock.api, async () => {
    throw Error("adaptive unavailable");
  });
  await p.load({ ...m, kind: "live" }, "https://gateway.test/live/3.ts", 0, {
    url: "https://source.test/three.m3u8",
    manifest: "hls",
  });
  assert.equal(p.state, "buffering");
  await clock.advance(1000);
  assert.equal(video.src, "https://source.test/three.m3u8");
  assert.equal(p.state, "playing");
  assert.equal(p.failureSummary, "");
  assert.ok(p.trace.some((t) => t.event === "freeview-native-fallback"));
  p.stop();
});
test("TVNZ manifest refusal surfaces its safe error immediately instead of looping retries", async () => {
  const { AdaptiveFault } = await import("../src/adaptive-fault.ts");
  const clock = new Clock(),
    video = new LiveVideo();
  const p = new LgPlayback(video as any, clock.api, async () => ({
    async attach() {},
    async load() {
      throw new AdaptiveFault(1001, "manifest", 403);
    },
    async destroy() {},
  }));
  await p.load({ ...m, kind: "live" }, "https://gateway.test/live/1.ts", 0, {
    url: "https://source.test/tvnz.mpd",
    manifest: "dash",
    license: "https://license.test/wv",
  });
  assert.equal(p.state, "error");
  assert.match(
    p.failureSummary,
    /Channel playlist failed \(HTTP 403\). Code 1001/,
  );
  await clock.advance(60000);
  assert.ok(!p.trace.some((t) => t.event === "reconnect"));
  p.stop();
});
