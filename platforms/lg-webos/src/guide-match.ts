export interface GuideChannel {
  id: string;
  epgId?: string;
  name: string;
}
export function guideName(value: string) {
  return value
    .normalize("NFKC")
    .toLowerCase()
    .replace(/\b(?:uhd|fhd|hd|sd|4k|raw)\b/g, "")
    .replace(/[^\p{L}\p{N}]+/gu, "");
}
/** Declared IDs first; exact, unambiguous display-name aliases only. No fuzzy matching. */
export class GuideMatcher {
  private ids = new Map<string, Set<string>>();
  private names = new Map<string, Set<string>>();
  constructor(
    channels: GuideChannel[],
    overrides: Record<string, string> = {},
  ) {
    for (const c of channels) {
      const target = c.epgId || "ltv-" + c.id;
      for (const id of (overrides[c.id]
        ? [overrides[c.id]]
        : [target, "ltv-" + c.id]
      ).filter(Boolean)) {
        const set = this.ids.get(id) ?? new Set();
        set.add(target);
        this.ids.set(id, set);
      }
      if (overrides[c.id]) continue;
      const n = guideName(c.name),
        set = this.names.get(n) ?? new Set();
      set.add(target);
      this.names.set(n, set);
    }
  }
  register(id: string, names: string[]) {
    if (this.ids.has(id)) return;
    const candidates = new Set<string>();
    for (const name of names.filter((n) => guideName(n)))
      for (const target of this.names.get(guideName(name)) ?? [])
        candidates.add(target);
    if (candidates.size === 1) this.ids.set(id, candidates);
  }
  targets(id: string) {
    return [...(this.ids.get(id) ?? [])];
  }
}
export function guideHash(raw: string) {
  let h = 2166136261;
  for (const b of new TextEncoder().encode(raw)) {
    h ^= b;
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0).toString(16).padStart(8, "0");
}
