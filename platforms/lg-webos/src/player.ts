import {
  AdaptiveFault,
  adaptiveFault,
  adaptiveFailureMessage,
} from "./adaptive-fault";
import { createAdaptiveSession, AdaptiveSession } from "./adaptive";
import type { FreeviewSource } from "./freeview";
import { Media } from "./model";
export type PlayerState =
  | "idle"
  | "loading"
  | "playing"
  | "paused"
  | "buffering"
  | "error"
  | "ended";
export interface Trace {
  at: number;
  event: string;
  state: PlayerState;
  code?: number;
  exception?: string;
  scriptLine?: number;
  scriptColumn?: number;
  elapsed?: number;
  attemptElapsed?: number;
  position?: number;
  duration?: number;
  readyState?: number;
  networkState?: number;
  paused?: boolean;
  ended?: boolean;
  bufferedAhead?: number;
  frames?: number;
  width?: number;
  height?: number;
  attempt?: number;
  failures?: number;
}
export interface PlaybackClock {
  now: () => number;
  timeout: (fn: () => void, ms: number) => ReturnType<typeof setTimeout>;
  cancelTimeout: (id: ReturnType<typeof setTimeout> | undefined) => void;
  interval: (fn: () => void, ms: number) => ReturnType<typeof setInterval>;
  cancelInterval: (id: ReturnType<typeof setInterval> | undefined) => void;
  foreground: () => boolean;
}
const defaults: PlaybackClock = {
  now: () => performance.now(),
  timeout: (fn, ms) => setTimeout(fn, ms),
  cancelTimeout: (id) => clearTimeout(id),
  interval: (fn, ms) => setInterval(fn, ms),
  cancelInterval: (id) => clearInterval(id),
  foreground: () => typeof document === "undefined" || !document.hidden,
};
/** One native video surface. Recovery is bounded per outage, not per viewing session. */
export class LgPlayback {
  state: PlayerState = "idle";
  media?: Media;
  lastFailure = "";
  trace: Trace[] = [];
  onState: (state: PlayerState) => void = () => {};
  onProgress: (position: number, duration: number) => void = () => {};
  onEnded: () => void = () => {};
  onTrace: (trace: Trace[]) => void = () => {};
  private options = { startupTimeout: 22000, reconnects: 4 };
  configure(options: Partial<{ startupTimeout: number; reconnects: number }>) {
    this.options = { ...this.options, ...options };
  }
  private source?: FreeviewSource;
  private nativeFallback = false;
  private adaptiveProblem?: AdaptiveFault;
  get failureSummary() {
    return this.adaptiveProblem
      ? adaptiveFailureMessage(this.adaptiveProblem)
      : "";
  }
  private adaptive?: AdaptiveSession;
  private closing?: Promise<void>;
  private generation = 0;
  private attempt = 0;
  private attemptPrepared = false;
  private failures = 0;
  private url = "";
  private resumeAt = 0;
  private opened = 0;
  private attemptOpened = 0;
  private lastProgress = 0;
  private lastPosition = 0;
  private lastFrames?: number;
  private healthySince?: number;
  private lastHeartbeat = 0;
  private wantsPlayback = false;
  private watchdog?: ReturnType<typeof setInterval>;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private pauseTimer?: ReturnType<typeof setTimeout>;
  private cleanups: (() => void)[] = [];
  constructor(
    public video: HTMLVideoElement,
    private clock: PlaybackClock = defaults,
    private adaptiveFactory = createAdaptiveSession,
  ) {}
  private frames() {
    try {
      const n =
        this.video.getVideoPlaybackQuality?.().totalVideoFrames ??
        (this.video as any).webkitDecodedFrameCount;
      return Number.isFinite(n) ? Number(n) : undefined;
    } catch {
      return undefined;
    }
  }
  private bufferedAhead() {
    try {
      const ranges = this.video.buffered;
      for (let i = 0; i < ranges.length; i++)
        if (ranges.start(i) <= this.position && ranges.end(i) >= this.position)
          return Math.round((ranges.end(i) - this.position) * 10) / 10;
    } catch {}
    return 0;
  }
  private exceptionInfo(error: unknown) {
    const name = (error as any)?.name;
    const exception = [
      "TypeError",
      "ReferenceError",
      "RangeError",
      "DOMException",
      "Error",
    ].includes(name)
      ? name
      : "unknown";
    const match = String((error as any)?.stack || "").match(
      /app\.js:(\d+):(\d+)/,
    );
    return {
      exception,
      ...(match
        ? { scriptLine: Number(match[1]), scriptColumn: Number(match[2]) }
        : {}),
    };
  }
  private log(
    event: string,
    code?: number,
    diagnostic?: {
      exception: string;
      scriptLine?: number;
      scriptColumn?: number;
    },
  ) {
    this.trace.push({
      at: Date.now(),
      event,
      state: this.state,
      code,
      ...diagnostic,
      elapsed: Math.round(this.clock.now() - this.opened),
      attemptElapsed: Math.round(this.clock.now() - this.attemptOpened),
      position: Math.round(this.position * 10) / 10,
      duration: this.duration,
      readyState: this.video.readyState,
      networkState: this.video.networkState,
      paused: this.video.paused,
      ended: this.video.ended,
      bufferedAhead: this.bufferedAhead(),
      frames: this.frames(),
      width: this.video.videoWidth || undefined,
      height: this.video.videoHeight || undefined,
      attempt: this.attempt,
      failures: this.failures,
    });
    if (this.trace.length > 180) this.trace.shift();
    this.onTrace(this.trace);
  }
  note(
    event:
      | "app-hidden"
      | "app-visible"
      | "pagehide"
      | "fullscreen"
      | "preview"
      | "panel-open"
      | "panel-close",
  ) {
    this.log(event);
  }
  private set(state: PlayerState) {
    if (this.state === state) return;
    this.state = state;
    this.log(state);
    this.onState(state);
  }
  async load(media: Media, url: string, position = 0, source?: FreeviewSource) {
    this.stop();
    this.media = media;
    this.source = source;
    this.nativeFallback = false;
    this.adaptiveProblem = undefined;
    this.url = source?.url ?? url;
    this.resumeAt = position;
    this.failures = 0;
    this.attempt = 0;
    this.lastFailure = "";
    this.wantsPlayback = true;
    this.opened = this.clock.now();
    this.lastProgress = this.opened;
    this.lastHeartbeat = this.opened;
    this.log("load");
    await this.start(this.generation);
  }
  private async start(gen: number) {
    try {
      await this.startAttempt(gen);
    } catch (error) {
      if (gen !== this.generation || !this.wantsPlayback) return;
      this.adaptiveProblem =
        error instanceof AdaptiveFault
          ? error
          : new AdaptiveFault(9003, "player");
      this.log(
        "startup-exception",
        Number((error as any)?.code) || 9003,
        this.exceptionInfo(error),
      );
      try {
        this.detach();
      } catch {
        /* Preserve the original failure if cleanup also fails. */
      }
      this.lastFailure = "startup-exception";
      this.set("error");
    }
  }
  private async startAttempt(gen: number) {
    if (gen !== this.generation || !this.wantsPlayback) return;
    this.log("startup-enter");
    if (this.closing) {
      this.log("teardown-wait");
      let timer: ReturnType<typeof setTimeout> | undefined;
      try {
        await Promise.race([
          this.closing,
          new Promise<void>((_, reject) => {
            timer = this.clock.timeout(
              () => reject(new AdaptiveFault(9010, "player")),
              8000,
            );
          }),
        ]);
      } catch (error) {
        if (gen === this.generation && this.wantsPlayback) {
          this.adaptiveProblem = new AdaptiveFault(9010, "player");
          this.lastFailure = "teardown-timeout";
          this.log("teardown-timeout", 9010);
          this.set("error");
        }
        return;
      } finally {
        this.clock.cancelTimeout(timer);
      }
    }
    if (gen !== this.generation || !this.wantsPlayback) return;
    this.detach();
    const attempt = ++this.attempt;
    this.attemptPrepared = false;
    this.attemptOpened = this.clock.now();
    this.lastPosition = 0;
    this.lastFrames = undefined;
    this.healthySince = undefined;
    this.lastProgress = this.clock.now();
    this.set("loading");
    const on = (name: string, fn: () => void) => {
      const guarded = () => {
        if (gen === this.generation && attempt === this.attempt) fn();
      };
      this.video.addEventListener(name, guarded);
      this.cleanups.push(() => this.video.removeEventListener(name, guarded));
    };
    let reportedFrame = false;
    const frame = () => {
      if (
        !reportedFrame &&
        gen === this.generation &&
        attempt === this.attempt
      ) {
        reportedFrame = true;
        this.log("first-decoded-frame");
      }
    };
    const frameVideo = this.video as any;
    if (typeof frameVideo.requestVideoFrameCallback === "function")
      frameVideo.requestVideoFrameCallback(frame);
    on("loadedmetadata", () => {
      if (
        this.media?.kind !== "live" &&
        this.resumeAt > 0 &&
        Number.isFinite(this.video.duration)
      )
        this.video.currentTime = Math.min(
          this.resumeAt,
          Math.max(0, this.video.duration - 2),
        );
      this.log("metadata");
    });
    on("playing", () => {
      this.clock.cancelTimeout(this.pauseTimer);
      this.pauseTimer = undefined;
      if (this.nativeFallback) this.adaptiveProblem = undefined;
      this.set("playing");
      this.sample();
    });
    on("pause", () => {
      this.log("native-pause");
      if (!this.attemptPrepared && this.wantsPlayback) return;
      if (!this.wantsPlayback) {
        this.set("paused");
        return;
      }
      if (
        this.media?.kind === "live" &&
        this.clock.foreground() &&
        !this.video.ended
      ) {
        this.set("buffering");
        this.clock.cancelTimeout(this.pauseTimer);
        this.pauseTimer = this.clock.timeout(() => {
          this.pauseTimer = undefined;
          if (
            gen === this.generation &&
            attempt === this.attempt &&
            this.wantsPlayback
          )
            void this.resumeNative(gen, attempt);
        }, 500);
      } else if (this.state !== "error" && this.state !== "ended")
        this.set("paused");
    });
    on("waiting", () => {
      if (this.wantsPlayback) this.set("buffering");
    });
    on("stalled", () => this.log("native-stalled"));
    on("timeupdate", () => {
      this.sample();
      if ((this.frames() ?? 0) > 0) frame();
      this.onProgress(this.position, this.duration);
    });
    on("error", () => {
      this.log("native-error", this.video.error?.code);
      this.recover(gen, "native-error");
    });
    on("ended", () => {
      this.log("native-ended");
      if (this.media?.kind === "live") {
        if (this.wantsPlayback) this.recover(gen, "live-eof");
        return;
      }
      this.wantsPlayback = false;
      this.onProgress(this.duration, this.duration);
      this.set("ended");
      this.onEnded();
    });
    for (const event of [
      "loadeddata",
      "canplay",
      "suspend",
      "abort",
      "emptied",
      "durationchange",
    ])
      on(event, () => this.log("native-" + event));
    // Reconnects request the same canonical gateway route, with a new browser cache identity.
    let target = this.url;
    if (attempt > 1 && !this.source) {
      const u = new URL(target);
      u.searchParams.set("_ltv_restart", `${Date.now()}-${attempt}`);
      target = u.toString();
    }
    this.watchdog = this.clock.interval(() => this.check(gen), 2000);
    const adaptive = !!this.source && !this.nativeFallback;
    try {
      if (adaptive) {
        const session = await this.adaptiveFactory((code, fatal, fault) => {
          if (gen !== this.generation || attempt !== this.attempt) return;
          this.log("adaptive-error", code);
          if (fatal)
            this.adaptiveFailure(
              gen,
              fault ?? new AdaptiveFault(code, "player"),
            );
        });
        if (gen !== this.generation || attempt !== this.attempt) {
          await session.destroy();
          return;
        }
        this.adaptive = session;
        this.log(
          this.source?.license ? "adaptive-widevine" : "adaptive-hls-hd",
        );
        await session.attach(this.video);
        if (gen !== this.generation || attempt !== this.attempt) return;
        await session.load(target, this.source?.license, this.source?.manifest);
        if (gen !== this.generation || attempt !== this.attempt) return;
      } else {
        this.video.src = target;
        this.video.load();
      }
      this.attemptPrepared = true;
      await this.video.play();
    } catch (error) {
      if (gen === this.generation && attempt === this.attempt) {
        if (adaptive) this.adaptiveFailure(gen, adaptiveFault(error));
        else this.recover(gen, "play-rejected");
      }
    }
  }
  private adaptiveFailure(gen: number, fault: AdaptiveFault) {
    if (this.retryTimer || this.state === "error") return;
    this.adaptiveProblem = fault;
    this.log(
      `adaptive-${fault.phase}${fault.status ? "-http-" + fault.status : ""}`,
      fault.code,
    );
    if (this.source?.manifest === "hls") {
      this.nativeFallback = true;
      this.log("freeview-native-fallback");
      this.recover(gen, "adaptive-hls-fallback");
    } else {
      const permanent =
        [9001, 9002, 6001].includes(fault.code) ||
        fault.status === 401 ||
        fault.status === 403;
      this.recover(gen, "adaptive-" + fault.phase, !permanent);
    }
  }
  reportUnhandled(error: unknown) {
    const kind = [
      "TypeError",
      "ReferenceError",
      "RangeError",
      "DOMException",
    ].includes((error as any)?.name)
      ? (error as any).name
      : "unknown";
    this.log("unhandled-promise-" + kind, Number((error as any)?.code) || 9003);
    if (this.media && this.wantsPlayback && !this.attempt) {
      this.adaptiveProblem = new AdaptiveFault(9003, "player");
      this.lastFailure = "startup-exception";
      this.set("error");
    }
  }
  private sample() {
    const now = this.clock.now(),
      position = this.position,
      frames = this.frames();
    const moved = Math.abs(position - this.lastPosition) > 0.1,
      decoded =
        frames !== undefined &&
        this.lastFrames !== undefined &&
        frames > this.lastFrames;
    if (moved || decoded) {
      if (position < this.lastPosition - 1) this.log("clock-discontinuity");
      this.lastProgress = now;
      this.healthySince ??= now;
      if (this.failures && now - this.healthySince >= 30000) {
        this.failures = 0;
        this.log("recovery-budget-reset");
      }
      this.lastPosition = position;
      this.lastFrames = frames;
      if (
        this.wantsPlayback &&
        this.state === "buffering" &&
        !this.retryTimer &&
        !this.video.paused
      )
        this.set("playing");
    } else if (this.lastFrames === undefined && frames !== undefined)
      this.lastFrames = frames;
  }
  private check(gen: number) {
    if (
      gen !== this.generation ||
      !this.media ||
      !this.wantsPlayback ||
      !this.clock.foreground() ||
      this.state === "error"
    )
      return;
    const now = this.clock.now();
    this.sample();
    if (now - this.lastHeartbeat >= 30000) {
      this.lastHeartbeat = now;
      this.log("heartbeat");
    }
    if (this.media.kind === "live" && this.video.ended) {
      this.recover(gen, "live-ended-without-event");
      return;
    }
    if (
      this.media.kind === "live" &&
      this.attemptPrepared &&
      this.video.paused &&
      !this.pauseTimer
    ) {
      void this.resumeNative(gen, this.attempt);
    }
    const timeout =
      this.state === "loading" ? this.options.startupTimeout : 20000;
    if (now - this.lastProgress > timeout) {
      this.log("startup-or-stall-timeout");
      this.recover(
        gen,
        this.state === "loading" ? "startup-timeout" : "stall-timeout",
      );
    }
  }
  private async resumeNative(gen: number, attempt: number) {
    if (
      gen !== this.generation ||
      attempt !== this.attempt ||
      !this.wantsPlayback ||
      !this.clock.foreground() ||
      this.retryTimer
    )
      return;
    this.log("resume-unexpected-pause");
    try {
      await this.video.play();
    } catch {
      if (gen === this.generation && attempt === this.attempt)
        this.recover(gen, "resume-rejected");
    }
  }
  private recover(gen: number, reason: string, retryable = true) {
    if (
      gen !== this.generation ||
      this.retryTimer ||
      this.state === "error" ||
      !this.wantsPlayback
    )
      return;
    this.lastFailure = reason;
    this.log(reason);
    this.detach();
    // Close the old native connection before starting a replacement; never open parallel feeds.
    this.release();
    this.healthySince = undefined;
    if (!retryable || this.failures >= this.options.reconnects) {
      this.set("error");
      return;
    }
    this.failures++;
    this.log("reconnect");
    this.resumeAt = this.media?.kind === "live" ? 0 : this.lastPosition;
    this.set("buffering");
    this.retryTimer = this.clock.timeout(
      () => {
        this.retryTimer = undefined;
        if (gen === this.generation) void this.start(gen);
      },
      Math.min(8000, 1000 * 2 ** (this.failures - 1)),
    );
  }
  private detach() {
    this.clock.cancelInterval(this.watchdog);
    this.watchdog = undefined;
    this.clock.cancelTimeout(this.pauseTimer);
    this.pauseTimer = undefined;
    this.cleanups.splice(0).forEach((fn) => fn());
  }
  private release() {
    const session = this.adaptive;
    this.adaptive = undefined;
    if (session) {
      const closing = (this.closing ?? Promise.resolve())
        .catch(() => {})
        .then(() => session.destroy())
        .catch(() => {});
      this.closing = closing;
      void closing.finally(() => {
        if (this.closing === closing) this.closing = undefined;
      });
    }
    this.video.pause();
    this.video.removeAttribute("src");
    this.video.load();
  }
  stop() {
    if (this.media) this.log("stop");
    this.generation++;
    this.wantsPlayback = false;
    this.clock.cancelTimeout(this.retryTimer);
    this.retryTimer = undefined;
    this.detach();
    if (this.media || this.video.getAttribute?.("src") || this.video.src)
      this.release();
    this.state = "idle";
    this.media = undefined;
  }
  pauseByUser() {
    this.wantsPlayback = false;
    this.clock.cancelTimeout(this.pauseTimer);
    this.pauseTimer = undefined;
    this.clock.cancelTimeout(this.retryTimer);
    this.retryTimer = undefined;
    this.log("user-pause");
    this.video.pause();
    this.set("paused");
  }
  async resumeByUser() {
    if (!this.media) return;
    this.wantsPlayback = true;
    this.lastProgress = this.clock.now();
    this.healthySince = undefined;
    this.log("user-resume");
    if (!this.video.getAttribute?.("src") && !this.video.src) {
      await this.start(this.generation);
      return;
    }
    try {
      await this.video.play();
    } catch {
      this.recover(this.generation, "user-resume-rejected");
    }
  }
  async toggle() {
    if (this.wantsPlayback) this.pauseByUser();
    else await this.resumeByUser();
  }
  seek(delta: number) {
    if (this.media?.kind === "live") return;
    this.video.currentTime = Math.max(
      0,
      Math.min(this.duration - 1, this.position + delta),
    );
    this.lastPosition = this.position;
    this.lastProgress = this.clock.now();
    this.healthySince = undefined;
  }
  get position() {
    return Number.isFinite(this.video.currentTime) ? this.video.currentTime : 0;
  }
  get duration() {
    return Number.isFinite(this.video.duration) ? this.video.duration : 0;
  }
  tracks(
    type: "audio" | "subtitle",
  ): { index: number; label: string; selected: boolean }[] {
    const list =
      type === "audio"
        ? (this.video as any).audioTracks
        : this.video.textTracks;
    if (!list) return [];
    return Array.from({ length: list.length }, (_, index) => ({
      index,
      label:
        list[index].label ||
        list[index].language ||
        `${type === "audio" ? "Audio" : "Subtitle"} ${index + 1}`,
      selected:
        type === "audio" ? list[index].enabled : list[index].mode === "showing",
    }));
  }
  selectTrack(type: "audio" | "subtitle", index: number) {
    const list =
      type === "audio"
        ? (this.video as any).audioTracks
        : this.video.textTracks;
    if (!list) return;
    for (let i = 0; i < list.length; i++)
      if (type === "audio") list[i].enabled = i === index;
      else list[i].mode = i === index ? "showing" : "disabled";
  }
}
