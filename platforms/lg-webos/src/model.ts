export type Kind = "live" | "movie" | "series" | "episode";
export interface Category {
  id: string;
  name: string;
  section: string;
  label: string;
}
export interface Media {
  id: string;
  kind: Kind;
  name: string;
  image: string;
  backdrop?: string;
  category: string;
  ext: string;
  rating: number;
  year?: string;
  plot?: string;
  genre?: string;
  parent?: string;
  season?: number;
  episode?: number;
  duration?: number;
  capabilities?: string[];
}
export interface Programme {
  id: string;
  title: string;
  description: string;
  start: number;
  end: number;
}
export interface SportEvent {
  id: string;
  externalId: string;
  sport: string;
  feedId: string;
  leagueName: string;
  title: string;
  subtitle?: string;
  venue?: string;
  startUtcMs: number;
  state: string;
  statusText: string;
  competitors: {
    id: string;
    name: string;
    shortName: string;
    crestUrl?: string;
    home: boolean;
    score?: string;
  }[];
  broadcasts: { name: string; market?: string }[];
  hero_art_url?: string;
  synopsis?: string;
  editorial_title?: string;
}
export interface Credentials {
  username: string;
  password: string;
}
export interface Profile {
  id: string;
  name: string;
}
export interface Progress {
  media: Media;
  position: number;
  duration: number;
  updated: number;
  complete: boolean;
}
export interface Details {
  media: Media;
  episodes: Media[];
  seasons: number[];
  cast: string;
  trailer?: string;
}
export const GATEWAY = "https://connect.loungetv.nz";
export const SERVICES = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1";
export const key = (m: Pick<Media, "kind" | "id">) => `${m.kind}:${m.id}`;
export const title = (s: string) =>
  s
    .replace(
      /^(?:(?:D\+|NF|AMZN|APTV|HBO|DSNY|ATV|NETFLIX|DISNEY\+|PRIME|APPLE\s?TV|HULU|PEACOCK|PARAMOUNT\+|STAN)\s+)+/i,
      "",
    )
    .trim();
export function category(row: Record<string, unknown>): Category {
  const name = String(row.category_name ?? "");
  const [section, ...rest] = name.split(" · ");
  return {
    id: String(row.category_id),
    name,
    section: rest.length ? section : name,
    label: rest.length ? rest.join(" · ") : name,
  };
}
export function media(row: Record<string, any>, kind: Kind): Media {
  return {
    id: String(kind === "series" ? row.series_id : (row.stream_id ?? row.id)),
    kind,
    name:
      kind === "live"
        ? String(row.name)
        : title(String(row.name ?? row.title ?? "")),
    image: String(row.stream_icon ?? row.cover ?? row.info?.movie_image ?? ""),
    backdrop: row.backdrop_path?.[0],
    category: String(row.category_id ?? ""),
    ext: String(
      row.container_extension ?? (kind === "live" ? "ts" : "mp4"),
    ).replace(/[^a-z0-9]/gi, ""),
    rating: Number(row.rating ?? 0),
    year: row.year,
    plot: row.plot,
    genre: row.genre,
    duration: Number(row.info?.duration_secs ?? 0),
    capabilities: Array.isArray(row.lounge_capabilities)
      ? row.lounge_capabilities
      : [],
  };
}
export function decodeText(value: unknown): string {
  const s = String(value ?? "");
  if (!s || s.length % 4 || !/^[A-Za-z0-9+/]*={0,2}$/.test(s)) return s;
  try {
    const bytes = Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
    const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    return /[\u0000-\u0008\u000e-\u001f]/.test(text) ? s : text;
  } catch {
    return s;
  }
}
export function programmes(rows: Record<string, any>[]): Programme[] {
  return rows
    .map((r) => ({
      id: String(r.id ?? r.start_timestamp),
      title: decodeText(r.title),
      description: decodeText(r.description),
      start: Number(r.start_timestamp) * 1000,
      end: Number(r.stop_timestamp ?? r.end_timestamp) * 1000,
    }))
    .filter(
      (p) =>
        Number.isFinite(p.start) && Number.isFinite(p.end) && p.end > p.start,
    )
    .sort((a, b) => a.start - b.start);
}
export const nowProgramme = (rows: Programme[], now = Date.now()) =>
  rows.find((p) => p.start <= now && p.end > now);
export const nzTime = (ms: number) =>
  new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    hour: "numeric",
    minute: "2-digit",
  }).format(ms);
export const nzDay = (ms: number) =>
  new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(ms);
export const nzDate = (ms: number) =>
  new Intl.DateTimeFormat("en-NZ", {
    timeZone: "Pacific/Auckland",
    weekday: "short",
    day: "numeric",
    month: "short",
  }).format(ms);
export const duration = (seconds: number) =>
  `${Math.floor(seconds / 3600) ? Math.floor(seconds / 3600) + ":" : ""}${String(Math.floor(seconds / 60) % 60).padStart(2, "0")}:${String(Math.floor(seconds) % 60).padStart(2, "0")}`;
export function sportLabel(s: string) {
  return (
    (
      {
        soccer: "Football",
        football: "NFL",
        basketball: "Basketball",
        rugby: "Rugby",
        "rugby-league": "League",
        cricket: "Cricket",
        mma: "Combat sports",
        racing: "Motorsport",
        tennis: "Tennis",
        hockey: "Ice hockey",
        baseball: "Baseball",
        golf: "Golf",
      } as Record<string, string>
    )[s] ?? s
  );
}
export function selectEvents(
  events: SportEvent[],
  time: string,
  sport: string,
  now = Date.now(),
): SportEvent[] {
  return events
    .filter((e) => !["cancelled", "postponed"].includes(e.state))
    .filter((e) => sport === "all" || e.sport === sport)
    .filter((e) =>
      time === "live"
        ? e.state === "live"
        : time === "today"
          ? nzDay(e.startUtcMs) === nzDay(now)
          : e.startUtcMs >= now && e.state !== "finished",
    )
    .sort((a, b) => a.startUtcMs - b.startUtcMs);
}
export function headlineEvents(
  events: SportEvent[],
  now = Date.now(),
): SportEvent[] {
  const terms = [
    "ufc",
    "boxing",
    "world cup",
    "nrl",
    "super league",
    "rugby",
    "all blacks",
    "international",
    "six nations",
    "cricket",
    "ashes",
    "ipl",
    "big bash",
    "t20",
    "nba",
    "basketball association",
    "wnba",
    "nbl",
    "nfl",
    "super bowl",
    "premier league",
    "champions league",
    "a-league",
    "formula",
    "f1",
    "motogp",
    "atp",
    "wta",
    "pga",
    "lpga",
    "mlb",
    "nhl",
  ];
  const pool = events
    .filter(
      (e) =>
        (e.state === "live" ||
          (e.state === "scheduled" &&
            e.startUtcMs >= now &&
            e.startUtcMs < now + 7 * 86400000)) &&
        terms.some((t) =>
          (e.title + " " + e.leagueName + " " + e.feedId)
            .toLowerCase()
            .includes(t),
        ),
    )
    .sort(
      (a, b) =>
        priority(b, now) - priority(a, now) || a.startUtcMs - b.startUtcMs,
    );
  const out: SportEvent[] = [];
  for (const e of pool) if (!out.some((x) => x.sport === e.sport)) out.push(e);
  for (const e of pool)
    if (!out.includes(e) && out.filter((x) => x.sport === e.sport).length < 4)
      out.push(e);
  for (const e of pool) if (!out.includes(e)) out.push(e);
  return out.slice(0, 12);
}
function priority(e: SportEvent, now: number) {
  const s = (e.title + " " + e.leagueName).toLowerCase();
  return (
    (e.state === "live" ? 45 : e.startUtcMs - now < 86400000 ? 18 : 10) +
    (/world cup|international|championship|ufc|test match|final/.test(s)
      ? 60
      : 35) +
    (/new zealand|all blacks|black caps|warriors|breakers|gallen/.test(s)
      ? 20
      : 0) +
    ({
      mma: 32,
      rugby: 26,
      "rugby-league": 26,
      cricket: 20,
      soccer: 20,
      football: 17,
      basketball: 17,
    }[e.sport] ?? 14)
  );
}
