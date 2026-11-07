import { installTvLicenseTransport } from "./tvnz-license";
import { AdaptiveFault, adaptiveFault } from "./adaptive-fault";
export interface AdaptiveSession {
  attach(video: HTMLVideoElement): Promise<void>;
  load(url: string, license?: string, manifest?: "hls" | "dash"): Promise<void>;
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
  onError: (code: number, fatal: boolean, fault?: AdaptiveFault) => void,
): Promise<AdaptiveSession> {
  const shaka = await shakaLibrary().catch(() => {
    throw new AdaptiveFault(9002, "player");
  });
  shaka.polyfill.installAll();
  if (!shaka.Player.isBrowserSupported())
    throw new AdaptiveFault(9001, "player");
  const player = new shaka.Player();
  player.addEventListener("error", (e: any) => {
    const fault = adaptiveFault(e.detail);
    onError(fault.code, e.detail?.severity === 2, fault);
  });
  return {
    attach: (video) => player.attach(video),
    async load(url, license, manifest = license ? "dash" : "hls") {
      const channel = license?.match(
        /^https:\/\/c\.mjh\.nz\/(tvnz-[12])-wv$/,
      )?.[1];
      const nativeLicense = channel && installTvLicenseTransport(shaka);
      player.configure({
        drm: license
          ? {
              servers: {
                "com.widevine.alpha": nativeLicense
                  ? "loungetv-license:" + channel
                  : license,
              },
              preferredKeySystems: ["com.widevine.alpha"],
              advanced: {
                "com.widevine.alpha": {
                  videoRobustness: ["HW_SECURE_ALL", ""],
                  headers: { "Content-Type": "application/octet-stream" },
                },
              },
            }
          : { servers: {} },
        abr: {
          enabled: true,
          defaultBandwidthEstimate: 8000000,
          restrictions: { maxHeight: 1080 },
        },
        streaming: {
          bufferingGoal: 12,
          rebufferingGoal: 2,
          preferNativeHls: false,
        },
        manifest: { retryParameters: { maxAttempts: 2, timeout: 15000 } },
      });
      try {
        await player.load(
          url,
          undefined,
          manifest === "dash"
            ? "application/dash+xml"
            : "application/x-mpegurl",
        );
      } catch (error) {
        throw adaptiveFault(error, "manifest");
      }
    },
    destroy: () => player.destroy(),
  };
}
