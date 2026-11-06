import { Media } from "./model";
export type PlayerState =
  "idle" | "loading" | "playing" | "paused" | "buffering" | "error" | "ended";
export interface Trace {
  at: number;
  event: string;
  state: PlayerState;
  code?: number;
  elapsed?: number;
  position?: number;
}
export class LgPlayback {
  state: PlayerState = "idle";
  media?: Media;
  private generation = 0;
  private retries = 0;
  private url = "";
  private resumeAt = 0;
  private opened = 0;
  private lastProgress = 0;
  private lastPosition = 0;
  private watchdog?: ReturnType<typeof setInterval>;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private cleanups: (() => void)[] = [];
  trace: Trace[] = [];
  onState: (state: PlayerState) => void = () => {};
  onProgress: (position: number, duration: number) => void = () => {};
  onEnded: () => void = () => {};
  constructor(public video: HTMLVideoElement) {}
  private log(event: string, code?: number) {
    this.trace.push({
      at: Date.now(),
      event,
      state: this.state,
      code,
      elapsed: Date.now() - this.opened,
      position: this.position,
    });
    if (this.trace.length > 160) this.trace.shift();
  }
  private set(state: PlayerState) {
    this.state = state;
    this.log(state);
    this.onState(state);
  }
  async load(media: Media, url: string, position = 0) {
    this.stop();
    this.media = media;
    this.url = url;
    this.resumeAt = position;
    this.retries = 0;
    this.opened = Date.now();
    this.lastProgress = Date.now();
    await this.start(this.generation);
  }
  private async start(gen: number) {
    if (gen !== this.generation) return;
    this.detach();
    this.set("loading");
    this.lastProgress = Date.now();
    this.lastPosition = 0;
    const on = (name: string, fn: () => void) => {
      const guarded = () => {
        if (gen === this.generation) fn();
      };
      this.video.addEventListener(name, guarded);
      this.cleanups.push(() => this.video.removeEventListener(name, guarded));
    };
    on("loadedmetadata", () => {
      if (this.resumeAt > 0 && Number.isFinite(this.video.duration))
        this.video.currentTime = Math.min(
          this.resumeAt,
          Math.max(0, this.video.duration - 2),
        );
      this.log("metadata");
    });
    on("playing", () => {
      this.lastProgress = Date.now();
      this.set("playing");
    });
    on("pause", () => {
      if (this.state !== "ended" && this.state !== "error") this.set("paused");
    });
    on("waiting", () => this.set("buffering"));
    on("stalled", () => this.log("stalled"));
    on("timeupdate", () => {
      const p = this.position;
      if (p > this.lastPosition + 0.1) {
        this.lastProgress = Date.now();
        this.lastPosition = p;
      }
      this.onProgress(p, this.duration);
    });
    on("error", () => {
      this.log("native-error", this.video.error?.code);
      this.recover(gen);
    });
    on("ended", () => {
      this.onProgress(this.duration, this.duration);
      this.set("ended");
      this.onEnded();
    });
    this.video.src = this.url;
    this.video.load();
    this.watchdog = setInterval(() => {
      if (gen !== this.generation) return;
      const timeout = this.state === "loading" ? 20000 : 18000;
      if (
        ["loading", "buffering", "playing"].includes(this.state) &&
        Date.now() - this.lastProgress > timeout
      ) {
        this.log("startup-or-stall-timeout");
        this.recover(gen);
      }
    }, 2000);
    try {
      await this.video.play();
    } catch {
      if (gen === this.generation && this.state !== "error") this.recover(gen);
    }
  }
  private recover(gen: number) {
    if (gen !== this.generation || this.retryTimer || this.state === "error")
      return;
    this.detach();
    if (this.retries >= 2) {
      this.video.pause();
      this.set("error");
      return;
    }
    this.retries++;
    this.log("reconnect");
    this.resumeAt = this.media?.kind === "live" ? 0 : this.position;
    this.set("buffering");
    this.retryTimer = setTimeout(() => {
      this.retryTimer = undefined;
      void this.start(gen);
    }, 1200 * this.retries);
  }
  private detach() {
    clearInterval(this.watchdog);
    this.cleanups.splice(0).forEach((fn) => fn());
  }
  stop() {
    this.generation++;
    clearTimeout(this.retryTimer);
    this.retryTimer = undefined;
    this.detach();
    this.video.pause();
    this.video.removeAttribute("src");
    this.video.load();
    this.state = "idle";
    this.media = undefined;
  }
  async toggle() {
    if (this.video.paused) {
      try {
        await this.video.play();
      } catch {
        this.set("error");
      }
    } else this.video.pause();
  }
  seek(delta: number) {
    if (this.media?.kind === "live") return;
    this.video.currentTime = Math.max(
      0,
      Math.min(this.duration - 1, this.position + delta),
    );
    this.lastProgress = Date.now();
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
