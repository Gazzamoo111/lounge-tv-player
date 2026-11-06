import { hasCurrentGuide, mergeGuide } from "./guide-policy";
import { discoverSports, mergeFixtures } from "./sports";
import { guideHash } from "./guide-match";
import {
  Category,
  Credentials,
  Details,
  GATEWAY,
  Kind,
  Media,
  Programme,
  SERVICES,
  SportEvent,
  category,
  media,
  programmes,
} from "./model";
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function request(
  url: string,
  signal?: AbortSignal,
  timeout = 25000,
): Promise<any> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  const abort = () => controller.abort();
  signal?.addEventListener("abort", abort, { once: true });
  try {
    const r = await fetch(url, {
      signal: controller.signal,
      credentials: "omit",
      cache: "no-store",
    });
    if (!r.ok)
      throw new ApiError(
        r.status,
        r.status === 401 || r.status === 403
          ? "Please check your Lounge TV login."
          : "Lounge TV could not load this content. Try again.",
      );
    return await r.json();
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError(
      0,
      controller.signal.aborted
        ? "The connection timed out. Try again."
        : "Unable to connect to Lounge TV. Check your internet connection.",
    );
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", abort);
  }
}
export class LoungeClient {
  constructor(private auth: Credentials) {}
  url(action?: string, params: Record<string, string> = {}) {
    const q = new URLSearchParams({
      ...this.auth,
      ...(action ? { action } : {}),
      ...params,
    });
    return `${GATEWAY}/player_api.php?${q}`;
  }
  async login() {
    const d = await request(this.url());
    if (
      Number(d.user_info?.auth) !== 1 ||
      String(d.user_info.status).toLowerCase() !== "active"
    )
      throw new ApiError(
        401,
        "Your Lounge TV login is incorrect or your membership is inactive.",
      );
    return d.user_info;
  }
  async categories(kind: Kind): Promise<Category[]> {
    const data = await request(
      this.url(
        kind === "live"
          ? "get_live_categories"
          : kind === "movie"
            ? "get_vod_categories"
            : "get_series_categories",
      ),
    );
    if (!Array.isArray(data))
      throw new ApiError(502, "The Lounge TV catalogue is unavailable.");
    return data.map(category);
  }
  async catalogue(kind: Kind): Promise<Media[]> {
    const data = await request(
      this.url(
        kind === "live"
          ? "get_live_streams"
          : kind === "movie"
            ? "get_vod_streams"
            : "get_series",
      ),
    );
    if (!Array.isArray(data))
      throw new ApiError(502, "The Lounge TV catalogue is unavailable.");
    return data.map((r) => media(r, kind));
  }
  guideUrl() {
    return `${GATEWAY}/xmltv.php?${new URLSearchParams({ ...this.auth })}`;
  }
  async guide(
    id: string,
    epgId?: string,
    useGateway = true,
  ): Promise<Programme[]> {
    let saved: Programme[] = [];
    if (epgId)
      try {
        const d = await request(
          `https://raw.githubusercontent.com/Gazzamoo111/lounge-tv-player/epg-data/epg/short/${guideHash(epgId)}.json`,
        );
        const raw = Array.isArray(d)
          ? d
          : (d.epg_listings ?? d.programmes ?? []);
        const rows = programmes(raw).filter((p) => p.end > Date.now());
        if (hasCurrentGuide(rows)) return rows;
        saved = rows;
      } catch {}
    if (!useGateway) return saved;
    try {
      const d = await request(
        this.url("get_short_epg", { stream_id: id, limit: "80" }),
      );
      return mergeGuide(programmes(d.epg_listings ?? []), saved);
    } catch (e) {
      if (saved.length) return saved;
      throw e;
    }
  }
  async details(item: Media): Promise<Details> {
    const d = await request(
      this.url(item.kind === "movie" ? "get_vod_info" : "get_series_info", {
        [item.kind === "movie" ? "vod_id" : "series_id"]: item.id,
      }),
    );
    if (d.error || !d.info)
      throw new ApiError(502, "Details are unavailable for this title.");
    const i = d.info;
    const merged = {
      ...item,
      plot: i.plot ?? i.description ?? item.plot,
      genre: i.genre,
      year: String(
        i.year ??
          i.releasedate?.slice(0, 4) ??
          i.releaseDate?.slice(0, 4) ??
          "",
      ),
      rating: Number(i.rating ?? item.rating),
      image: i.movie_image ?? i.cover ?? item.image,
      backdrop: i.backdrop_path?.[0] ?? item.backdrop,
      duration: Number(i.duration_secs ?? 0),
      ext: d.movie_data?.container_extension ?? item.ext,
    };
    const raw = Array.isArray(d.episodes)
      ? d.episodes
      : Object.values(d.episodes ?? {}).flat();
    const episodes: Media[] = raw
      .map((r: any) => ({
        ...media({ ...r, name: r.title }, "episode"),
        parent: item.id,
        season: Number(r.season),
        episode: Number(r.episode_num),
        image: r.info?.movie_image ?? item.image,
        plot: r.info?.plot ?? "",
        name: r.title || `Episode ${r.episode_num}`,
      }))
      .sort(
        (a: Media, b: Media) =>
          a.season! - b.season! || a.episode! - b.episode!,
      );
    return {
      media: merged,
      episodes,
      seasons: [...new Set(episodes.map((e) => e.season!))],
      cast: i.cast ?? "",
      trailer: i.youtube_trailer,
    };
  }
  playback(item: Media) {
    const type = item.kind === "episode" ? "series" : item.kind;
    return `${GATEWAY}/${type}/${encodeURIComponent(this.auth.username)}/${encodeURIComponent(this.auth.password)}/${encodeURIComponent(item.id)}.${item.ext}`;
  }
  async events(
    onPartial?: (events: SportEvent[]) => void,
  ): Promise<SportEvent[]> {
    const now = Date.now();
    const q = new URLSearchParams({
      from: String(now - 12 * 3600000),
      to: String(now + 7 * 86400000),
    });
    const [discovery, calendar] = await Promise.allSettled([
      discoverSports((url) => request(url, undefined, 10000), now, onPartial),
      request(`${SERVICES}/lounge-sports-calendar?${q}`),
    ]);
    const primary =
      discovery.status === "fulfilled" ? discovery.value.events : [];
    const extra =
      calendar.status === "fulfilled" && Array.isArray(calendar.value.events)
        ? calendar.value.events
        : [];
    if (
      !primary.length &&
      !extra.length &&
      (discovery.status === "rejected" ||
        discovery.value.failed === discovery.value.total)
    )
      throw new ApiError(502, "The sports schedule is unavailable.");
    return mergeFixtures([...primary, ...extra], now);
  }

  async candidates(
    event: SportEvent,
  ): Promise<{ media: Media; confidence: string }[]> {
    const q = new URLSearchParams({
      sport: event.sport,
      league: event.leagueName,
      title: event.title,
      home: event.competitors.find((x) => x.home)?.name ?? "",
      away: event.competitors.find((x) => !x.home)?.name ?? "",
      broadcasters: event.broadcasts.map((x) => x.name).join("|"),
    });
    const d = await request(`${SERVICES}/lounge-sports-candidates?${q}`);
    return (d.candidates ?? [])
      .filter(
        (r: any) =>
          Number(r.providerItemId) > 0 &&
          ["definite", "likely", "maybe"].includes(r.confidence),
      )
      .map((r: any) => ({
        media: {
          id: String(r.providerItemId),
          kind: "live",
          name: r.name,
          image: r.logoUrl ?? "",
          category: "sports-backend",
          ext: "ts",
          rating: 0,
        },
        confidence: r.confidence,
      }));
  }
  sportPlayback(item: Media) {
    const q = new URLSearchParams({ ...this.auth });
    return `${SERVICES}/lounge-core-xtream/sport/_/_/${item.id}.ts?${q}`;
  }
}
// Never import or embed BEST credentials, provider endpoints or admin keys in this client.
