import qrcode from "qrcode-generator";
import type { Credentials } from "./model";
import { SERVICE_URI as URI } from "./identity";
export function phoneService(
  method: string,
  payload: Record<string, unknown> = {},
): Promise<any> {
  return new Promise((resolve, reject) => {
    const Bridge = (window as any).PalmServiceBridge;
    if (!Bridge)
      return reject(Error("Phone sign-in is available on your LG TV."));
    const bridge = new Bridge();
    const timer = setTimeout(() => {
      bridge.cancel?.();
      reject(
        Error(
          "Phone sign-in could not reach the TV service. Try again or use the remote.",
        ),
      );
    }, 7000);
    bridge.onservicecallback = (raw: string) => {
      clearTimeout(timer);
      try {
        const result = JSON.parse(raw);
        result.returnValue
          ? resolve(result)
          : reject(Error(result.errorText || "Phone sign-in is unavailable."));
      } catch (error) {
        reject(error);
      }
    };
    try {
      bridge.call(URI + method, JSON.stringify(payload));
    } catch (error) {
      clearTimeout(timer);
      reject(error);
    }
  });
}
export function qrMarkup(url: string) {
  const qr = qrcode(0, "M");
  qr.addData(url);
  qr.make();
  return qr.createSvgTag({ cellSize: 6, margin: 24, scalable: true });
}
export function beginPhoneLogin(options: {
  ready: (url: string, expires: number) => void;
  credentials: (auth: Credentials) => void;
  error: (message: string) => void;
}) {
  let cancelled = false,
    token = "",
    timer: ReturnType<typeof setTimeout> | undefined;
  const cancel = () => {
    cancelled = true;
    clearTimeout(timer);
    if (token) void phoneService("stop", { token }).catch(() => {});
  };
  const poll = async () => {
    if (cancelled) return;
    try {
      const status = await phoneService("take", { token });
      if (cancelled) return;
      if (status.credentials) {
        cancelled = true;
        options.credentials(status.credentials);
        return;
      }
      timer = setTimeout(poll, 1500);
    } catch (error) {
      if (!cancelled) options.error((error as Error).message);
    }
  };
  void phoneService("start")
    .then((result) => {
      token = result.token;
      if (cancelled) {
        void phoneService("stop", { token }).catch(() => {});
        return;
      }
      options.ready(result.url, result.expires);
      void poll();
    })
    .catch((error) => {
      if (!cancelled) options.error(error.message);
    });
  return cancel;
}
