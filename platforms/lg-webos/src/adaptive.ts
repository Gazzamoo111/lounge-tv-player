export interface AdaptiveSession {
  attach(video: HTMLVideoElement): Promise<void>;
  load(url: string, license: string): Promise<void>;
  destroy(): Promise<void>;
}
let library: Promise<any> | undefined;
function shakaLibrary(): Promise<any> {
  const ready = (window as any).shaka;
  if (ready) return Promise.resolve(ready);
  return (library ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "vendor/shaka-player.compiled.js";
    script.onload = () => {
      const s = (window as any).shaka;
      if (s) resolve(s);
      else reject(Error("Adaptive player unavailable"));
    };
    script.onerror = () => {
      library = undefined;
      script.remove();
      reject(Error("Adaptive player unavailable"));
    };
    document.head.append(script);
  }));
}
export async function createAdaptiveSession(
  onError: (code: number, fatal: boolean) => void,
): Promise<AdaptiveSession> {
  const shaka = await shakaLibrary();
  shaka.polyfill.installAll();
  if (!shaka.Player.isBrowserSupported())
    throw Error("Adaptive playback is unavailable on this TV");
  const player = new shaka.Player();
  player.addEventListener("error", (e: any) =>
    onError(Number(e.detail?.code) || 0, e.detail?.severity === 2),
  );
  return {
    attach: (video) => player.attach(video),
    async load(url, license) {
      player.configure({
        drm: { servers: { "com.widevine.alpha": license } },
        streaming: { bufferingGoal: 12, rebufferingGoal: 2 },
        manifest: { retryParameters: { maxAttempts: 2, timeout: 15000 } },
      });
      await player.load(url, undefined, "application/dash+xml");
    },
    destroy: () => player.destroy(),
  };
}
