import { SERVICE_URI } from "./identity";
import { AdaptiveFault } from "./adaptive-fault";
/** The TV's genuine CDM challenge passes unchanged to its normal license service. */
export function installTvLicenseTransport(shaka: any) {
  const Bridge = (window as any).PalmServiceBridge;
  if (!Bridge || !shaka.net?.NetworkingEngine?.registerScheme) return false;
  shaka.net.NetworkingEngine.registerScheme(
    "loungetv-license",
    (uri: string, request: any) => {
      const failure = (code: number, status?: number) => {
        if (!shaka.util.Error)
          return new AdaptiveFault(code, "license", status);
        if (code === 7001) return new shaka.util.Error(2, 7, 7001);
        if (status)
          return new shaka.util.Error(2, 1, 1001, uri, status, null, {}, 2);
        return new shaka.util.Error(
          2,
          6,
          6007,
          new AdaptiveFault(code, "license"),
        );
      };
      const channel = uri.replace(/^loungetv-license:/, "");
      if (!/^tvnz-[12]$/.test(channel))
        return shaka.util.AbortableOperation.failed(failure(9030));
      const bridge = new Bridge();
      let timer: ReturnType<typeof setTimeout> | undefined,
        rejectRequest: (error: unknown) => void = () => {};
      const promise = new Promise((resolve, reject) => {
        rejectRequest = reject;
        const bytes =
          request.body instanceof ArrayBuffer
            ? new Uint8Array(request.body)
            : new Uint8Array(
                request.body.buffer,
                request.body.byteOffset || 0,
                request.body.byteLength,
              );
        let binary = "";
        for (let i = 0; i < bytes.length; i++)
          binary += String.fromCharCode(bytes[i]);
        timer = setTimeout(() => {
          bridge.cancel?.();
          reject(failure(6007));
        }, 15000);
        bridge.onservicecallback = (raw: string) => {
          clearTimeout(timer);
          try {
            const result = JSON.parse(raw);
            if (!result.returnValue)
              return reject(
                failure(
                  Number(result.errorCode) || 6007,
                  Number(result.httpStatus) || undefined,
                ),
              );
            const encoded = atob(result.data),
              output = new Uint8Array(encoded.length);
            for (let i = 0; i < output.length; i++)
              output[i] = encoded.charCodeAt(i);
            resolve({
              uri,
              originalUri: uri,
              data: output.buffer,
              headers: {},
              status: result.httpStatus || 200,
            });
          } catch {
            reject(failure(6007));
          }
        };
        bridge.call(
          SERVICE_URI + "license",
          JSON.stringify({ channel, challenge: btoa(binary) }),
        );
      });
      return new shaka.util.AbortableOperation(promise, () => {
        clearTimeout(timer);
        bridge.cancel?.();
        rejectRequest(failure(7001));
        return Promise.resolve();
      });
    },
  );
  return true;
}
