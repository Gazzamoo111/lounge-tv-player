import type { Media } from "./model";
export interface FreeviewSource {
  url: string;
  manifest: "hls" | "dash";
  license?: string;
}
/** Audited Android v5.2.3.10 loungeNzPlaybackChannel equivalents. */
export function freeviewSource(item: Media): FreeviewSource | undefined {
  if (item.kind !== "live") return;
  const id = item.epgId ?? "",
    name = item.name
      .normalize("NFKD")
      .replace(/\p{M}/gu, "")
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, "");
  const channels: Record<string, [string, "hls" | "dash", string?]> = {
    "mjh-tvnz-1": ["tvnz-1", "dash", "tvnz-1-wv"],
    "mjh-tvnz-2": ["tvnz-2", "dash", "tvnz-2-wv"],
    "mjh-three": ["three", "hls"],
    "mjh-maori-tv": ["maori-tv", "hls"],
    "mjh-te-reo": ["te-reo", "hls"],
  };
  // Name fallback only covers the two unique TVNZ brands. Other generic names require exact EPG identity.
  const row =
    channels[id] ??
    (name === "TVNZ1"
      ? channels["mjh-tvnz-1"]
      : name === "TVNZ2"
        ? channels["mjh-tvnz-2"]
        : undefined);
  if (!row) return;
  return {
    url: `https://i.mjh.nz/.r/${row[0]}.${row[1] === "dash" ? "mpd" : "m3u8"}`,
    manifest: row[1],
    ...(row[2] ? { license: "https://c.mjh.nz/" + row[2] } : {}),
  };
}
