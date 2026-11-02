import postgres from "npm:postgres@3.4.7";

const rawDbUrl = Deno.env.get("SUPABASE_DB_URL");
if (!rawDbUrl) throw new Error("SUPABASE_DB_URL missing");

function transactionPoolUrl(raw: string) {
  const u = new URL(raw);
  const ref = u.hostname.split(".")[1] || "liqocisegeqzbyuqloql";
  u.hostname = "aws-0-ap-southeast-2.pooler.supabase.com";
  u.port = "5432";
  u.username = `postgres.${ref}`;
  return u.toString();
}

const sql = postgres(transactionPoolUrl(rawDbUrl), {
  prepare: false,
  max: 2,
  idle_timeout: 5,
  connect_timeout: 10,
});

const RELEASE_KEY = Deno.env.get("LOUNGE_RELEASE_KEY") || "universal-v1-staging";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "https://liqocisegeqzbyuqloql.supabase.co";
const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "private, max-age=30",
  "Access-Control-Allow-Origin": "*",
};

const clamp = (v: string | null, fallback: number, min: number, max: number) => {
  const n = Number(v ?? fallback);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, Math.floor(n))) : fallback;
};
const epoch = (v: unknown) => v ? Math.floor(new Date(String(v)).getTime() / 1000) : null;
const json = (body: unknown, status = 200, extra: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { ...JSON_HEADERS, ...extra } });
const xmlEsc = (v: unknown) => String(v ?? "")
  .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
  .replace(/"/g, "&quot;").replace(/'/g, "&apos;");

function credentials(req: Request, url: URL, parts: string[]) {
  const header = req.headers.get("Authorization") || "";
  if (header.startsWith("Basic ")) {
    try {
      const decoded = atob(header.slice(6));
      const split = decoded.indexOf(":");
      if (split >= 0) return { username: decoded.slice(0, split), password: decoded.slice(split + 1) };
    } catch (_) {}
  }
  return {
    username: url.searchParams.get("username") || parts[1] || "",
    password: url.searchParams.get("password") || parts[2] || "",
  };
}

async function release() {
  const rows = await sql`
    select id,release_key,state,live_expected,movie_expected,series_expected
    from lounge_delivery.catalogue_releases
    where release_key=${RELEASE_KEY} and state in ('staging','active')
    limit 1
  `;
  return rows[0] || null;
}

async function auth(username: string, password: string) {
  if (!username || !password) return null;
  const rows = await sql`select * from lounge_delivery.authenticate(${username},${password})`;
  return rows[0] || null;
}

function customerBase(url: URL) {
  const explicitBase = url.searchParams.get("_lounge_base") || Deno.env.get("LOUNGE_PUBLIC_BASE") || "";
  if (explicitBase) return explicitBase.endsWith("/") ? explicitBase.slice(0, -1) : explicitBase;
  const host = url.searchParams.get("_lounge_host") || Deno.env.get("LOUNGE_PUBLIC_HOST") || url.host;
  return `${url.protocol}//${host}`;
}

function pageHeaders(page: number, limit: number, count: number) {
  return {
    "X-Lounge-Page": String(page),
    "X-Lounge-Limit": String(limit),
    "X-Lounge-Count": String(count),
    "X-Lounge-Has-More": count === limit ? "1" : "0",
  };
}

async function categoryRows(releaseId: string, kind: "live" | "movie" | "series") {
  return await sql`
    select id::text category_id,display_name category_name
    from lounge_delivery.categories
    where release_id=${releaseId}::uuid and kind=${kind} and enabled
    order by sort_order,id
  `;
}

async function liveRows(releaseId: string, categoryId: number | null, page: number, limit: number) {
  const offset = (page - 1) * limit;
  if (categoryId) {
    return await sql`
      select i.external_id,l.display_name,l.logo_url,l.category,l.subcategory,l.sort_order,c.id category_id
      from lounge_delivery.category_members m
      join lounge_delivery.categories c on c.id=m.category_id and c.release_id=m.release_id and c.kind='live' and c.enabled
      join lounge_delivery.live_release_items l on l.release_id=m.release_id and l.live_channel_id=m.canonical_id and l.admission='keep'
      join lounge_delivery.item_ids i on i.item_kind='live' and i.canonical_id=l.live_channel_id
      where m.release_id=${releaseId}::uuid and m.category_id=${categoryId} and m.item_kind='live'
      order by m.rank desc,l.sort_order,i.external_id
      limit ${limit} offset ${offset}
    `;
  }
  return await sql`
    select i.external_id,l.display_name,l.logo_url,l.category,l.subcategory,l.sort_order,null::bigint category_id
    from lounge_delivery.live_release_items l
    join lounge_delivery.item_ids i on i.item_kind='live' and i.canonical_id=l.live_channel_id
    where l.release_id=${releaseId}::uuid and l.admission='keep'
    order by l.sort_order,i.external_id
    limit ${limit} offset ${offset}
  `;
}

async function vodRows(releaseId: string, type: "movie" | "series", categoryId: number | null, page: number, limit: number) {
  const offset = (page - 1) * limit;
  if (categoryId) {
    return await sql`
      select i.external_id,v.title_id,v.display_name,v.poster_url,v.backdrop_url,t.release_year,t.release_date,t.overview,
             t.content_rating,t.quality_score,t.metadata,c.id category_id
      from lounge_delivery.category_members m
      join lounge_delivery.categories c on c.id=m.category_id and c.release_id=m.release_id and c.kind=${type} and c.enabled
      join lounge_delivery.vod_release_items v on v.release_id=m.release_id and v.title_id=m.canonical_id and v.content_type=${type} and v.admission='keep'
      join lounge_core.titles t on t.id=v.title_id
      join lounge_delivery.item_ids i on i.item_kind=${type}::lounge_delivery.item_kind and i.canonical_id=v.title_id
      where m.release_id=${releaseId}::uuid and m.category_id=${categoryId} and m.item_kind=${type}::lounge_delivery.item_kind
      order by m.rank desc,v.sort_order,i.external_id
      limit ${limit} offset ${offset}
    `;
  }
  return await sql`
    select i.external_id,v.title_id,v.display_name,v.poster_url,v.backdrop_url,t.release_year,t.release_date,t.overview,
           t.content_rating,t.quality_score,t.metadata,null::bigint category_id
    from lounge_delivery.vod_release_items v
    join lounge_core.titles t on t.id=v.title_id
    join lounge_delivery.item_ids i on i.item_kind=${type}::lounge_delivery.item_kind and i.canonical_id=v.title_id
    where v.release_id=${releaseId}::uuid and v.content_type=${type} and v.admission='keep'
    order by v.sort_order,i.external_id
    limit ${limit} offset ${offset}
  `;
}

async function movieInfo(releaseId: string, externalId: number) {
  const rows = await sql`
    select i.external_id,v.display_name,v.poster_url,v.backdrop_url,t.release_year,t.release_date,t.overview,t.content_rating,
           t.runtime_minutes,t.quality_score,t.metadata,
           coalesce((select s.file_ext from lounge_core.sources s where s.title_id=t.id and s.status not in ('rejected','missing','hidden')
             order by case s.source_role when 'primary' then 0 when 'backup' then 1 else 2 end,s.runtime_verified desc,s.quality_score desc limit 1),'mp4') file_ext
    from lounge_delivery.item_ids i
    join lounge_delivery.vod_release_items v on v.title_id=i.canonical_id and v.release_id=${releaseId}::uuid and v.content_type='movie' and v.admission='keep'
    join lounge_core.titles t on t.id=v.title_id
    where i.item_kind='movie' and i.external_id=${externalId}
    limit 1
  `;
  return rows[0] || null;
}

async function seriesInfo(releaseId: string, externalId: number) {
  const seriesRows = await sql`
    select i.external_id,v.title_id,v.display_name,v.poster_url,v.backdrop_url,t.release_year,t.release_date,t.overview,t.content_rating,t.metadata
    from lounge_delivery.item_ids i
    join lounge_delivery.vod_release_items v on v.title_id=i.canonical_id and v.release_id=${releaseId}::uuid and v.content_type='series' and v.admission='keep'
    join lounge_core.titles t on t.id=v.title_id
    where i.item_kind='series' and i.external_id=${externalId}
    limit 1
  `;
  const s = seriesRows[0];
  if (!s) return null;
  const episodes = await sql`
    select se.id season_id,se.season_number,se.title season_title,se.poster_url season_poster,
           e.id episode_id,e.episode_number,e.title,e.overview,e.release_date,e.runtime_minutes,e.still_url,
           ei.external_id episode_external_id,
           coalesce((select es.file_ext from lounge_core.episode_sources es where es.episode_id=e.id and es.status not in ('rejected','missing','hidden')
             order by case es.source_role when 'primary' then 0 when 'backup' then 1 else 2 end,es.runtime_verified desc,es.quality_score desc limit 1),'mp4') file_ext
    from lounge_core.seasons se
    join lounge_core.episodes e on e.season_id=se.id and e.status<>'excluded'
    join lounge_delivery.item_ids ei on ei.item_kind='episode' and ei.canonical_id=e.id
    where se.series_title_id=${s.title_id}::uuid and se.status<>'excluded'
    order by se.season_number,e.episode_number
  `;
  return { series: s, episodes };
}

async function nativeItems(releaseId: string, categoryId: number, page: number, limit: number) {
  const offset = (page - 1) * limit;
  return await sql`
    select m.item_kind::text item_kind,i.external_id,m.rank,
           coalesce(l.display_name,v.display_name,t.canonical_title) display_name,
           coalesce(l.logo_url,v.poster_url,t.poster_url) poster_url,
           coalesce(v.backdrop_url,t.backdrop_url) backdrop_url,
           t.release_year,t.release_date,t.overview,t.content_rating,t.metadata
    from lounge_delivery.category_members m
    join lounge_delivery.categories c on c.id=m.category_id and c.release_id=m.release_id and c.enabled
    join lounge_delivery.item_ids i on i.item_kind=m.item_kind and i.canonical_id=m.canonical_id
    left join lounge_delivery.live_release_items l
      on m.item_kind='live' and l.release_id=m.release_id and l.live_channel_id=m.canonical_id and l.admission='keep'
    left join lounge_delivery.vod_release_items v
      on m.item_kind in ('movie','series') and v.release_id=m.release_id and v.title_id=m.canonical_id and v.admission='keep'
    left join lounge_core.titles t on t.id=v.title_id
    where m.release_id=${releaseId}::uuid and m.category_id=${categoryId}
      and ((m.item_kind='live' and l.live_channel_id is not null) or (m.item_kind in ('movie','series') and v.title_id is not null))
    order by m.rank desc,coalesce(l.sort_order,v.sort_order,0),i.external_id
    limit ${limit} offset ${offset}
  `;
}

async function shortEpg(releaseId: string, externalId: number, limit: number) {
  return await sql`
    select p.start_at,p.end_at,p.title,p.description
    from lounge_delivery.item_ids i
    join lounge_delivery.live_release_items l on l.live_channel_id=i.canonical_id and l.release_id=${releaseId}::uuid and l.admission='keep'
    join lounge_core.live_epg_programmes p on p.live_channel_id=l.core_live_channel_id
    where i.item_kind='live' and i.external_id=${externalId} and p.end_at>now()-interval '1 hour'
    order by p.start_at
    limit ${limit}
  `;
}

function xmltvTime(v: unknown) {
  const d = new Date(String(v));
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}${pad(d.getUTCMonth()+1)}${pad(d.getUTCDate())}${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())} +0000`;
}

async function xmltv(releaseId: string) {
  const channels = await sql`
    select i.external_id,l.live_channel_id,l.display_name,l.logo_url
    from lounge_delivery.live_release_items l
    join lounge_delivery.item_ids i on i.item_kind='live' and i.canonical_id=l.live_channel_id
    where l.release_id=${releaseId}::uuid and l.admission='keep'
    order by l.sort_order,i.external_id
  `;
  const programmes = await sql`
    select i.external_id,p.start_at,p.end_at,p.title,p.description
    from lounge_delivery.live_release_items l
    join lounge_delivery.item_ids i on i.item_kind='live' and i.canonical_id=l.live_channel_id
    join lounge_core.live_epg_programmes p on p.live_channel_id=l.core_live_channel_id
    where l.release_id=${releaseId}::uuid and l.admission='keep'
      and p.end_at>now()-interval '1 hour' and p.start_at<now()+interval '7 days'
    order by i.external_id,p.start_at
  `;
  const out = ['<?xml version="1.0" encoding="UTF-8"?>','<tv generator-info-name="Lounge TV">'];
  for (const c of channels) {
    out.push(`<channel id="ltv-${c.external_id}"><display-name>${xmlEsc(c.display_name)}</display-name>${c.logo_url ? `<icon src="${xmlEsc(c.logo_url)}"/>` : ''}</channel>`);
  }
  for (const p of programmes) {
    out.push(`<programme start="${xmltvTime(p.start_at)}" stop="${xmltvTime(p.end_at)}" channel="ltv-${p.external_id}"><title lang="en">${xmlEsc(p.title)}</title>${p.description ? `<desc lang="en">${xmlEsc(p.description)}</desc>` : ''}</programme>`);
  }
  out.push('</tv>');
  return new Response(out.join(''), { headers: { 'Content-Type':'application/xml; charset=utf-8','Cache-Control':'private, max-age=600' } });
}

async function m3u(url: URL, releaseId: string, username: string, password: string) {
  const base = customerBase(url);
  const rows = await sql`
    select i.external_id,l.display_name,l.logo_url,l.category,l.subcategory,l.sort_order,
           coalesce(c.display_name,l.category) category_name
    from lounge_delivery.live_release_items l
    join lounge_delivery.item_ids i on i.item_kind='live' and i.canonical_id=l.live_channel_id
    left join lateral (
      select c.display_name
      from lounge_delivery.category_members m
      join lounge_delivery.categories c on c.id=m.category_id and c.kind='live' and c.enabled
      where m.release_id=l.release_id and m.item_kind='live' and m.canonical_id=l.live_channel_id
      order by c.sort_order,c.id limit 1
    ) c on true
    where l.release_id=${releaseId}::uuid and l.admission='keep'
    order by l.sort_order,i.external_id
  `;
  const lines = [`#EXTM3U x-tvg-url="${base}/xmltv.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}"`];
  let no = 1;
  for (const r of rows) {
    const group = String(r.category_name || r.category || "Live TV");
    lines.push(`#EXTINF:-1 channel-id="ltv-${r.external_id}" tvg-id="ltv-${r.external_id}" tvg-logo="${xmlEsc(r.logo_url || "")}" tvg-chno="${no++}" group-title="${xmlEsc(group)}",${String(r.display_name)}`);
    lines.push(`${base}/live/${encodeURIComponent(username)}/${encodeURIComponent(password)}/${r.external_id}.ts`);
  }
  return new Response(lines.join("\n") + "\n", {
    headers: { "Content-Type": "application/vnd.apple.mpegurl; charset=utf-8", "Cache-Control": "private, max-age=120" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET" && req.method !== "HEAD") return json({ error: "method_not_allowed" }, 405);
  try {
    const url = new URL(req.url);
    if (url.pathname.endsWith("/health")) {
      const rows = await sql`select id,release_key,state,lounge_delivery.validate_release(id) validation from lounge_delivery.catalogue_releases where release_key=${RELEASE_KEY} limit 1`;
      return json({ ok: true, service: "lounge-catalogue-staging", production: false, release: rows[0] || null });
    }
    const parts = url.pathname.split("/").filter(Boolean);
    const { username, password } = credentials(req, url, parts);
    const account = await auth(username, password);
    if (!account) return json({ user_info: { auth: 0, status: "Disabled" }, error: "invalid_lounge_credentials" }, 401);

    const rel = await release();
    if (!rel) return json({ error: "catalogue_release_not_ready", release_key: RELEASE_KEY }, 503);

    if (url.pathname.includes("/v1/catalogue")) {
      const page = clamp(url.searchParams.get("page"), 1, 1, 100000);
      const limit = clamp(url.searchParams.get("limit"), 60, 1, 200);

      if (url.pathname.endsWith("/v1/catalogue") || url.pathname.endsWith("/v1/catalogue/")) {
        return json({
          release: rel.release_key,
          sections: ["MOVIES", "SERIES", "STREAMING", "KIDS & FAMILY"],
          pagination: { default_limit: 60, max_limit: 200 },
        });
      }

      if (url.pathname.endsWith("/v1/catalogue/categories")) {
        const kind = String(url.searchParams.get("kind") || "");
        if (!["live","movie","series","streaming","kids"].includes(kind)) return json({ error: "bad_kind" }, 400);
        const rows = await sql`
          select id,kind,category_key,display_name,parent_key,sort_order
          from lounge_delivery.categories
          where release_id=${rel.id}::uuid and kind=${kind} and enabled
          order by sort_order,id
        `;
        return json({ items: rows });
      }

      if (url.pathname.endsWith("/v1/catalogue/items")) {
        const categoryId = Number(url.searchParams.get("category_id") || 0);
        if (!Number.isFinite(categoryId) || categoryId <= 0) return json({ error: "category_id_required" }, 400);
        const rows = await nativeItems(rel.id, categoryId, page, limit);
        return json({ items: rows, page, limit, has_more: rows.length === limit });
      }

      if (url.pathname.endsWith("/v1/catalogue/title")) {
        const kind = String(url.searchParams.get("kind") || "");
        const id = Number(url.searchParams.get("id") || 0);
        if (kind === "movie") return json({ item: await movieInfo(rel.id, id) });
        if (kind === "series") return json({ item: await seriesInfo(rel.id, id) });
        return json({ error: "bad_kind" }, 400);
      }

      return json({ error: "not_found" }, 404);
    }

    const action = url.searchParams.get("action") || "";
    const page = clamp(url.searchParams.get("page"), 1, 1, 100000);
    const limit = url.searchParams.has("limit")
      ? clamp(url.searchParams.get("limit"), 250, 1, 10000)
      : 10000;
    const categoryRaw = Number(url.searchParams.get("category_id") || 0);
    const categoryId = Number.isFinite(categoryRaw) && categoryRaw > 0 ? categoryRaw : null;

    if (url.pathname.endsWith("/get.php")) return await m3u(url, rel.id, username, password);
    if (url.pathname.endsWith("/xmltv.php")) return await xmltv(rel.id);

    if (!action) {
      return json({
        user_info: {
          username,
          password: "",
          auth: 1,
          status: "Active",
          exp_date: epoch(account.expires_at),
          is_trial: "0",
          active_cons: "0",
          created_at: null,
          max_connections: String(account.connection_limit || 1),
          allowed_output_formats: ["m3u8", "ts"],
        },
        server_info: {
          url: customerBase(url).replace(/^https?:\/\//, ""),
          port: "443",
          https_port: "443",
          server_protocol: "https",
          timezone: "Pacific/Auckland",
          timestamp_now: Math.floor(Date.now() / 1000),
          time_now: new Date().toISOString(),
        },
      });
    }

    if (action === "get_live_categories") return json(await categoryRows(rel.id, "live"));
    if (action === "get_vod_categories") return json(await categoryRows(rel.id, "movie"));
    if (action === "get_series_categories") return json(await categoryRows(rel.id, "series"));

    if (action === "get_live_streams") {
      const rows = await liveRows(rel.id, categoryId, page, limit);
      return json(rows.map((r: any, i: number) => ({
        num: (page - 1) * limit + i + 1,
        name: String(r.display_name), stream_type: "live", stream_id: Number(r.external_id),
        stream_icon: r.logo_url || "", epg_channel_id: `ltv-${r.external_id}`,
        added: "", category_id: r.category_id ? String(r.category_id) : "0",
        custom_sid: "", tv_archive: 0, direct_source: "", tv_archive_duration: 0,
      })), 200, pageHeaders(page, limit, rows.length));
    }

    if (action === "get_vod_streams") {
      const rows = await vodRows(rel.id, "movie", categoryId, page, limit);
      return json(rows.map((r: any, i: number) => ({
        num: (page - 1) * limit + i + 1,
        name: String(r.display_name), stream_type: "movie", stream_id: Number(r.external_id),
        stream_icon: r.poster_url || "", rating: r.metadata?.vote_average ?? r.quality_score ?? "",
        rating_5based: null, added: r.release_date || "", category_id: r.category_id ? String(r.category_id) : "0",
        container_extension: "mp4", custom_sid: null, direct_source: "",
      })), 200, pageHeaders(page, limit, rows.length));
    }

    if (action === "get_series") {
      const rows = await vodRows(rel.id, "series", categoryId, page, limit);
      return json(rows.map((r: any, i: number) => ({
        num: (page - 1) * limit + i + 1,
        name: String(r.display_name), series_id: Number(r.external_id), cover: r.poster_url || "",
        plot: r.overview || "", cast: r.metadata?.cast || "", director: r.metadata?.director || "",
        genre: r.metadata?.genres || "", releaseDate: r.release_date || "", rating: r.metadata?.vote_average ?? r.quality_score ?? "",
        category_id: r.category_id ? String(r.category_id) : "0", backdrop_path: r.backdrop_url ? [r.backdrop_url] : [],
      })), 200, pageHeaders(page, limit, rows.length));
    }

    if (action === "get_vod_info") {
      const id = Number(url.searchParams.get("vod_id") || 0);
      const r = await movieInfo(rel.id, id);
      if (!r) return json({ error: "not_found" }, 404);
      return json({
        info: {
          movie_image: r.poster_url || "", tmdb_id: r.metadata?.tmdb_id ?? null, name: r.display_name,
          o_name: r.display_name, releasedate: r.release_date, plot: r.overview || "", cast: r.metadata?.cast || "",
          director: r.metadata?.director || "", genre: r.metadata?.genres || "", duration_secs: r.runtime_minutes ? Number(r.runtime_minutes) * 60 : null,
          duration: r.runtime_minutes ? `${r.runtime_minutes} min` : "", rating: r.metadata?.vote_average ?? r.quality_score ?? "",
          backdrop_path: r.backdrop_url ? [r.backdrop_url] : [],
        },
        movie_data: { stream_id: Number(r.external_id), name: r.display_name, container_extension: r.file_ext || "mp4", custom_sid: null, direct_source: "" },
      });
    }

    if (action === "get_series_info") {
      const id = Number(url.searchParams.get("series_id") || 0);
      const data = await seriesInfo(rel.id, id);
      if (!data) return json({ error: "not_found" }, 404);
      const grouped: Record<string, any[]> = {};
      const seasons = new Map<number, any>();
      for (const e of data.episodes) {
        const sn = Number(e.season_number);
        seasons.set(sn, { air_date: null, episode_count: 0, id: String(e.season_id), name: e.season_title || `Season ${sn}`, overview: "", season_number: sn, cover: e.season_poster || "" });
        const key = String(sn);
        grouped[key] ||= [];
        grouped[key].push({
          id: String(e.episode_external_id), episode_num: Number(e.episode_number), title: e.title || `Episode ${e.episode_number}`,
          container_extension: e.file_ext || "mp4", info: { movie_image: e.still_url || "", plot: e.overview || "", releasedate: e.release_date || "", duration_secs: e.runtime_minutes ? Number(e.runtime_minutes) * 60 : null },
          custom_sid: "", added: e.release_date || "", season: sn, direct_source: "",
        });
        seasons.get(sn).episode_count += 1;
      }
      const s = data.series;
      return json({
        seasons: [...seasons.values()].sort((a, b) => a.season_number - b.season_number),
        info: { name: s.display_name, cover: s.poster_url || "", plot: s.overview || "", releaseDate: s.release_date || "", rating: s.metadata?.vote_average || "", backdrop_path: s.backdrop_url ? [s.backdrop_url] : [] },
        episodes: grouped,
      });
    }

    if (action === "get_short_epg") {
      const id = Number(url.searchParams.get("stream_id") || 0);
      const n = clamp(url.searchParams.get("limit"), 6, 1, 20);
      const rows = await shortEpg(rel.id, id, n);
      return json({ epg_listings: rows.map((r: any) => ({
        id: null,
        epg_id: id,
        title: btoa(unescape(encodeURIComponent(String(r.title || "")))),
        lang: "en",
        start: r.start_at,
        end: r.end_at,
        description: btoa(unescape(encodeURIComponent(String(r.description || "")))),
        channel_id: `ltv-${id}`,
        start_timestamp: epoch(r.start_at),
        stop_timestamp: epoch(r.end_at),
      })) });
    }

    return json({ error: "unsupported_action", action }, 400);
  } catch (err) {
    console.error("LOUNGE_STAGING_CATALOGUE", err);
    return json({ error: "catalogue_unavailable" }, 503);
  }
});