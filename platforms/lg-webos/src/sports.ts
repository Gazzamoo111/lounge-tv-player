import { SportEvent, nzDay } from "./model";
import { mapLimit } from "./tasks";
/** Same discovery coverage as Android SportLoungeDiscovery.kt v5.2.3.10. */
export const DISCOVERY_FEEDS = [
  "football/nfl",
  "football/college-football",
  "rugby-league/3",
  "rugby/180659",
  "rugby/164205",
  "rugby/244293",
  "rugby/271937",
  "rugby/267979",
  "rugby/270559",
  "rugby/270557",
  "rugby/242041",
  "rugby/17567",
  "rugby/289234",
  "rugby/272073",
  "rugby/268565",
  "rugby/289237",
  "cricket/all",
  "basketball/nbl",
  "basketball/nba",
  "basketball/wnba",
  "basketball/mens-college-basketball",
  "hockey/nhl",
  "baseball/mlb",
  "soccer/aus.1",
  "soccer/eng.1",
  "soccer/uefa.champions",
  "racing/f1",
  "mma/ufc",
  "golf/pga",
  "tennis/atp",
  "tennis/wta",
];
const array = (v: any): any[] => (Array.isArray(v) ? v : []);
function state(s: any) {
  const n = String(s?.type?.name ?? "").toLowerCase();
  if (n.includes("cancel") || n.includes("abandon")) return "cancelled";
  if (n.includes("postpon")) return "postponed";
  return s?.type?.completed || s?.type?.state === "post"
    ? "finished"
    : s?.type?.state === "in"
      ? "live"
      : "scheduled";
}
function competitor(c: any) {
  const t = c.team ?? c.athlete ?? c;
  return {
    id: String(t.id ?? c.id ?? ""),
    name: String(t.displayName ?? t.fullName ?? t.name ?? ""),
    shortName: String(
      t.shortDisplayName ??
        t.shortName ??
        t.displayName ??
        t.fullName ??
        t.name ??
        "",
    ),
    crestUrl: t.logo ?? t.flag?.href ?? t.headshot?.href,
    home: c.homeAway === "home",
    score:
      typeof c.score === "object"
        ? c.score.displayValue
        : String(c.score ?? ""),
  };
}
function broadcast(c: any) {
  const names = array(c.broadcasts)
    .flatMap((b: any) => array(b.names))
    .concat(array(c.geoBroadcasts).map((b: any) => b.media?.shortName));
  return [...new Set(names.filter(Boolean))].map((name) => ({
    name: String(name),
  }));
}
export function parseScoreboard(feed: string, d: any): SportEvent[] {
  const sport = feed.split("/")[0],
    league = d.leagues?.[0]?.name ?? feed.split("/")[1];
  const make = (e: any, c: any, id: string, title?: string): SportEvent => ({
    id: "espn:" + feed + ":" + id,
    externalId: id,
    sport,
    feedId: feed,
    leagueName: league,
    title: title ?? e.name ?? "",
    subtitle: c.type?.abbreviation,
    venue: c.venue?.fullName,
    startUtcMs: Date.parse(c.date ?? e.date),
    state: state(c.status ?? e.status),
    statusText: c.status?.type?.shortDetail ?? c.status?.type?.detail ?? "",
    competitors: array(c.competitors)
      .slice(0, sport === "racing" ? 3 : 2)
      .map(competitor),
    broadcasts: broadcast(c),
  });
  if (sport === "cricket")
    return array(d.sports).flatMap((s) =>
      array(s.leagues).flatMap((l) =>
        array(l.events).map((e) => {
          const c = {
            ...e,
            status: e.fullStatus ?? { type: { state: e.status } },
          };
          const r = make(
            e,
            c,
            String(e.id),
            e.name ??
              array(e.competitors)
                .map((c) => competitor(c).name)
                .join(" v "),
          );
          r.leagueName = l.shortName ?? l.name ?? "Cricket";
          return r;
        }),
      ),
    );
  return array(d.events)
    .flatMap((e) => {
      if (sport === "tennis")
        return array(e.groupings).flatMap((g) =>
          array(g.competitions).map((c) =>
            make(
              e,
              c,
              String(c.id),
              array(c.competitors)
                .map((c) => competitor(c).name)
                .join(" v "),
            ),
          ),
        );
      const cs = array(e.competitions);
      if (!cs.length) return [];
      if (sport === "racing")
        return cs.map((c, i) => make(e, c, String(e.id) + "-" + i));
      if (sport === "mma") {
        const last = cs[cs.length - 1],
          live = cs.find((c) => state(c.status) === "live");
        const r = make(e, last, String(e.id));
        r.startUtcMs = Date.parse(cs[0].date ?? e.date);
        r.state = live
          ? "live"
          : cs.every((c) => state(c.status) === "finished")
            ? "finished"
            : "scheduled";
        return [r];
      }
      return [make(e, cs[0], String(e.id))];
    })
    .filter((e) => Number.isFinite(e.startUtcMs) && e.title);
}
export function mergeFixtures(rows: SportEvent[], now = Date.now()) {
  const map = new Map<string, SportEvent>(),
    fixture = new Map<string, string>();
  for (const e of rows) {
    if (
      !Number.isFinite(e.startUtcMs) ||
      e.startUtcMs < now - 12 * 3600000 ||
      e.startUtcMs > now + 7 * 86400000
    )
      continue;
    const names = e.competitors
      .map((c) => c.name.toLowerCase().replace(/[^a-z0-9]/g, ""))
      .sort()
      .join("|");
    const identity =
      e.sport +
      "|" +
      (names || e.title.toLowerCase()) +
      "|" +
      Math.floor(e.startUtcMs / 60000);
    const existingId = fixture.get(identity);
    if (existingId) {
      const old = map.get(existingId)!;
      map.set(existingId, {
        ...old,
        ...e,
        id: existingId,
        broadcasts: [
          ...new Map(
            [...old.broadcasts, ...e.broadcasts].map((b) => [b.name, b]),
          ).values(),
        ],
        hero_art_url: e.hero_art_url ?? old.hero_art_url,
      });
    } else {
      fixture.set(identity, e.id);
      map.set(e.id, e);
    }
  }
  return [...map.values()].sort((a, b) => a.startUtcMs - b.startUtcMs);
}
export async function discoverSports(
  read: (url: string) => Promise<any>,
  now = Date.now(),
  onPartial?: (events: SportEvent[]) => void,
) {
  const days = Array.from({ length: 7 }, (_, i) =>
    nzDay(now + i * 86400000).replace(/-/g, ""),
  );
  const months = [...new Set(days.map((d) => d.slice(0, 6)))];
  const jobs = DISCOVERY_FEEDS.flatMap((feed) =>
    feed === "cricket/all"
      ? days.map((date) => ({
          feed,
          url: `https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket&dates=${date}`,
        }))
      : months.map((month) => ({
          feed,
          url: `https://site.api.espn.com/apis/site/v2/sports/${feed}/scoreboard?dates=${month}&limit=1000`,
        })),
  );
  let failed = 0;
  const partial: SportEvent[] = [];
  const results = await mapLimit(jobs, 4, async (j) => {
    try {
      const rows = parseScoreboard(j.feed, await read(j.url));
      partial.push(...rows);
      onPartial?.(mergeFixtures(partial, now));
      return rows;
    } catch {
      failed++;
      return [];
    }
  });
  return {
    events: mergeFixtures(results.flat(), now),
    failed,
    total: jobs.length,
  };
}
