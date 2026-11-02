const PROJECT_FUNCTIONS = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1";
const SLUG = "/lounge-universal-staging";

function routeFrom(pathname: string) {
  const idx = pathname.indexOf(SLUG);
  if (idx < 0) return { suffix: "/player_api.php", basePath: SLUG };
  const basePath = pathname.slice(0, idx + SLUG.length);
  const suffix = pathname.slice(idx + SLUG.length) || "/player_api.php";
  return { suffix, basePath };
}

function isPlayback(path: string) {
  return path.startsWith("/live/") || path.startsWith("/movie/") || path.startsWith("/series/");
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, range",
    "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
  };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (req.method !== "GET" && req.method !== "HEAD") {
    return Response.json({ error: "method_not_allowed" }, { status: 405, headers: corsHeaders() });
  }

  const incoming = new URL(req.url);
  const { suffix, basePath } = routeFrom(incoming.pathname);

  if (suffix === "/health") {
    return Response.json(
      { ok: true, service: "Lounge TV Universal Catalogue Staging", production: false },
      { headers: { ...corsHeaders(), "Cache-Control": "no-store" } },
    );
  }

  const playback = isPlayback(suffix);
  const targetSlug = playback ? "lounge-source-resolver-staging" : "lounge-catalogue-staging";
  const target = new URL(`${PROJECT_FUNCTIONS}/${targetSlug}${suffix}`);
  target.search = incoming.search;

  const publicBase = `${PROJECT_FUNCTIONS}${SLUG}`;
  if (!playback) target.searchParams.set("_lounge_base", publicBase);

  const headers = new Headers(req.headers);
  headers.set("X-Forwarded-Host", incoming.host);
  headers.set("X-Forwarded-Proto", "https");
  headers.set("X-Lounge-Staging-Gateway", "1");
  if (playback) {
    const clientKey = `${req.headers.get("CF-Connecting-IP") || "unknown"}|${req.headers.get("User-Agent") || "unknown"}`;
    headers.set("X-Lounge-Client-Key", clientKey);
  }

  const response = await fetch(target, {
    method: req.method,
    headers,
    redirect: "manual",
    signal: AbortSignal.timeout(playback ? 35000 : 20000),
  });

  const out = new Headers(response.headers);
  for (const [k, v] of Object.entries(corsHeaders())) out.set(k, v);
  out.set("X-Lounge-Service", "Universal-Catalogue-Staging");
  out.set("X-Content-Type-Options", "nosniff");

  return new Response(req.method === "HEAD" ? null : response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: out,
  });
});