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

const sql = postgres(transactionPoolUrl(rawDbUrl), { prepare: false, max: 2, idle_timeout: 5, connect_timeout: 10 });
const RELEASE_KEY = Deno.env.get("LOUNGE_RELEASE_KEY") || "universal-v1-staging";
const JSON_HEADERS = { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });

async function sha256Hex(input: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function authenticate(username: string, password: string) {
  const rows = await sql`select * from lounge_delivery.authenticate(${username},${password})`;
  return rows[0] || null;
}

async function release() {
  const rows = await sql`
    select id from lounge_delivery.catalogue_releases
    where release_key=${RELEASE_KEY} and state in ('staging','active') limit 1
  `;
  return rows[0] || null;
}

async function candidates(releaseId: string, serviceAssignmentId: string, kind: "live" | "movie" | "episode", externalId: number) {
  return await sql`
    select target_url,request_headers,drm_scheme,drm_license_url,source_rank
    from lounge_delivery.playback_candidates(
      ${releaseId}::uuid,
      ${serviceAssignmentId}::uuid,
      ${kind}::lounge_delivery.item_kind,
      ${externalId}
    )
  `;
}

function forwardHeaders(req: Request, extra: Record<string, unknown> | null) {
  const h = new Headers();
  const range = req.headers.get("Range");
  if (range) h.set("Range", range);
  h.set("Accept", req.headers.get("Accept") || "*/*");
  h.set("User-Agent", req.headers.get("User-Agent") || "LoungeTV-Gateway/2.0");
  if (extra && typeof extra === "object") {
    for (const [k, v] of Object.entries(extra)) if (v != null) h.set(k, String(v));
  }
  return h;
}

function responseHeaders(upstream: Response, failoverIndex: number) {
  const out = new Headers();
  for (const name of ["content-type", "content-length", "content-range", "accept-ranges", "last-modified", "etag"]) {
    const v = upstream.headers.get(name);
    if (v) out.set(name, v);
  }
  out.set("Cache-Control", "no-store");
  out.set("X-Lounge-Source", "resolved");
  out.set("X-Lounge-Failover", String(failoverIndex));
  out.set("X-Content-Type-Options", "nosniff");
  return out;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "GET" && req.method !== "HEAD") return json({ error: "method_not_allowed" }, 405);
  try {
    const url = new URL(req.url);
    if (url.pathname.endsWith("/health")) return json({ ok: true, service: "lounge-source-resolver-staging", production: false });
    const parts = url.pathname.split("/").filter(Boolean);
    const route = parts.findIndex((x) => x === "live" || x === "movie" || x === "series");
    if (route < 0 || parts.length < route + 4) return json({ error: "bad_playback_path" }, 404);

    const routeKind = parts[route] as "live" | "movie" | "series";
    const username = decodeURIComponent(parts[route + 1] || "");
    const password = decodeURIComponent(parts[route + 2] || "");
    const externalId = Number(String(parts[route + 3] || "").split(".")[0]);
    if (!Number.isFinite(externalId) || externalId <= 0) return json({ error: "bad_lounge_id" }, 400);

    const account = await authenticate(username, password);
    if (!account) return json({ error: "invalid_lounge_credentials" }, 401);
    const rel = await release();
    if (!rel) return json({ error: "catalogue_release_not_ready" }, 503);

    const itemKind: "live" | "movie" | "episode" = routeKind === "series" ? "episode" : routeKind;
    const suppliedClient = req.headers.get("X-Lounge-Client-Key") || `${req.headers.get("CF-Connecting-IP") || "unknown"}|${req.headers.get("User-Agent") || "unknown"}`;
    const clientKey = await sha256Hex(suppliedClient);
    const limit = Math.max(1, Number(account.connection_limit || 1));
    const reserved = await sql`select lounge_delivery.reserve_playback_slot(${String(account.credential_id)}::uuid,${itemKind}::lounge_delivery.item_kind,${externalId},${clientKey},${limit},120) ok`;
    if (!reserved[0]?.ok) return json({ error: "connection_limit_reached", max_connections: limit }, 429);

    const rows = await candidates(String(rel.id), String(account.service_assignment_id), itemKind, externalId);
    if (!rows.length) return json({ error: "source_unavailable" }, 404);

    let lastStatus = 502;
    for (let i = 0; i < rows.length; i++) {
      const candidate = rows[i];
      try {
        const upstream = await fetch(String(candidate.target_url), {
          method: req.method,
          headers: forwardHeaders(req, candidate.request_headers),
          redirect: "follow",
          signal: AbortSignal.timeout(routeKind === "live" ? 15000 : 30000),
        });
        lastStatus = upstream.status;
        if (!upstream.ok && upstream.status !== 206) continue;
        return new Response(req.method === "HEAD" ? null : upstream.body, {
          status: upstream.status,
          statusText: upstream.statusText,
          headers: responseHeaders(upstream, i),
        });
      } catch (err) {
        console.warn("LOUNGE_SOURCE_FAILOVER", i, String(err));
      }
    }
    return json({ error: "all_sources_failed", last_status: lastStatus }, 502);
  } catch (err) {
    console.error("LOUNGE_SOURCE_RESOLVER", err);
    return json({ error: "playback_unavailable" }, 503);
  }
});