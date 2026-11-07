export type AdaptivePhase = "player" | "manifest" | "segment" | "license";
export class AdaptiveFault extends Error {
  constructor(
    public code: number,
    public phase: AdaptivePhase,
    public status?: number,
  ) {
    super(
      `Adaptive ${phase} failure ${code}${status ? ` HTTP ${status}` : ""}`,
    );
  }
}
/** Keep only typed codes. Error payloads may contain signed URLs, bodies and headers. */
export function adaptiveFault(
  error: any,
  phase: AdaptivePhase = "player",
): AdaptiveFault {
  if (error instanceof AdaptiveFault) return error;
  const code = Number(error?.code) || 9000;
  const data = Array.isArray(error?.data) ? error.data : [];
  if (code === 1001 || code === 1002 || code === 1003) {
    const request = Number(data[code === 1001 ? 4 : code === 1002 ? 2 : 1]);
    const mapped =
      request === 0
        ? "manifest"
        : request === 1
          ? "segment"
          : request === 2
            ? "license"
            : phase;
    const status =
      code === 1001 &&
      Number.isInteger(data[1]) &&
      data[1] >= 100 &&
      data[1] <= 599
        ? data[1]
        : undefined;
    return new AdaptiveFault(code, mapped, status);
  }
  if (code >= 6000 && code < 7000) {
    const nested = data.find(
      (value: any) =>
        value && typeof value === "object" && Number(value.code) >= 1000,
    );
    const inner = nested ? adaptiveFault(nested, "license") : undefined;
    return new AdaptiveFault(code, "license", inner?.status);
  }
  return new AdaptiveFault(code, phase);
}
export function adaptiveFailureMessage(fault: AdaptiveFault) {
  if (fault.code === 9003)
    return "The player could not start. Open Playback details for the startup record. Code 9003.";
  if (fault.code === 9010)
    return "The previous player did not close. Restart Lounge TV before trying this channel again. Code 9010.";
  if (fault.code === 9001)
    return "This TV did not expose the required adaptive playback capability. Code 9001.";
  if (fault.code === 9002)
    return "The bundled adaptive player could not load. Code 9002.";
  const label = {
    manifest: "Channel playlist",
    segment: "Video segment",
    license: "DRM / license",
    player: "Adaptive player",
  }[fault.phase];
  return `${label} failed${fault.status ? ` (HTTP ${fault.status})` : ""}. Code ${fault.code}.`;
}
