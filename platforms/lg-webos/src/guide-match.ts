export interface GuideChannel {
  id: string;
  epgId?: string;
  name: string;
  cacheKey?: string;
  region?: string;
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
  private identities = new Map<string, string>();
  constructor(
    channels: GuideChannel[],
    overrides: Record<string, string> = {},
  ) {
    for (const c of channels) {
      const target = c.cacheKey || c.epgId || "ltv-" + c.id;
      const aliases = regionalGuideAliases(c);
      this.identities.set(
        target,
        (c.region ?? "") + ":" + (c.epgId || "ltv-" + c.id),
      );
      for (const id of (overrides[c.id]
        ? [overrides[c.id]]
        : [
            ...(aliases.replaceDeclared ? [] : [c.epgId || "ltv-" + c.id]),
            ...aliases.ids,
            "ltv-" + c.id,
          ]
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
    if (
      candidates.size &&
      new Set([...candidates].map((target) => this.identities.get(target)))
        .size === 1
    )
      this.ids.set(id, candidates);
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

/** Region must come from the playlist category or an explicit guide identifier. */
export function channelRegion(
  channel: { name: string; epgId?: string },
  category = "",
) {
  const text = category.toLowerCase();
  if (/new zealand|\bnz\b/.test(text)) return "nz";
  if (/australia|\bau\b|\baus\b/.test(text)) return "au";
  if (/united kingdom|\buk\b/.test(text)) return "uk";
  if (/united states|\bus\b|\busa\b/.test(text)) return "us";
  const id = channel.epgId ?? "",
    suffix = id.match(/\.(nz|au|uk|us)$/i)?.[1];
  if (suffix) return suffix.toLowerCase();
  if (id.startsWith("mjh-") && /tvnz|three|maori|te-reo/.test(id)) return "nz";
  return undefined;
}
export function regionalGuideAliases(c: GuideChannel): {
  ids: string[];
  replaceDeclared?: boolean;
} {
  if (c.region !== "au") return { ids: [] };
  const names: Record<string, string[]> = {
    seven: ["mjh-seven-syd"],
    channel7: ["mjh-seven-syd"],
    "7mate": ["mjh-7mate-syd"],
    "7two": ["mjh-7two-syd"],
    "7flix": ["mjh-7flix-syd"],
    channel9: ["mjh-channel-9-nsw"],
    "9gem": ["mjh-gem-nsw"],
    "9go": ["mjh-go-nsw"],
    "9life": ["mjh-life-nsw"],
    "9rush": ["mjh-rush-nsw"],
    ten: ["mjh-10-nsw"],
    channel10: ["mjh-10-nsw"],
    10: ["mjh-10-nsw"],
    "10bold": ["mjh-10bold-nsw"],
    "10drama": ["mjh-10bold-nsw"],
    "10peach": ["mjh-10peach-nsw"],
    "10comedy": ["mjh-10peach-nsw"],
    "10shake": ["mjh-10shake-nsw"],
    abctv: ["mjh-abc-nsw"],
    abc: ["mjh-abc-nsw"],
  };
  const name = guideName(c.name),
    ids = names[name] ?? [];
  // The current catalogue declares Ten as 10Bold.au. Those are different schedules.
  return {
    ids,
    replaceDeclared:
      ["ten", "channel10", "10"].includes(name) && c.epgId === "10Bold.au",
  };
}
