const DEFAULT_SUPABASE = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1";

function clientKey(request) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const ua = request.headers.get("User-Agent") || "unknown";
  return `${ip}|${ua}`;
}

export default {
  async fetch(request, env) {
    const incoming = new URL(request.url);

    if (incoming.pathname === "/health") {
      return Response.json(
        { ok: true, service: "LoungeTV Universal Catalogue Staging", production: false },
        { headers: { "Cache-Control": "no-store" } },
      );
    }

    const metadataRoute =
      incoming.pathname.startsWith("/v1/") ||
      incoming.pathname === "/" ||
      incoming.pathname === "/player_api.php" ||
      incoming.pathname === "/get.php" ||
      incoming.pathname === "/xmltv.php";

    const playbackRoute =
      incoming.pathname.startsWith("/live/") ||
      incoming.pathname.startsWith("/movie/") ||
      incoming.pathname.startsWith("/series/");

    if (!metadataRoute && !playbackRoute) return new Response("Not found", { status: 404 });

    const base = (env.SUPABASE_FUNCTIONS_BASE || DEFAULT_SUPABASE).replace(/\/+$/, "");
    const functionName = playbackRoute ? "lounge-source-resolver-staging" : "lounge-catalogue-staging";
    const normalizedPath = incoming.pathname === "/" ? "/player_api.php" : incoming.pathname;
    const upstream = new URL(`${base}/${functionName}${normalizedPath}`);
    upstream.search = incoming.search;
    upstream.searchParams.set("_lounge_host", incoming.hostname);

    const headers = new Headers(request.headers);
    headers.set("X-Forwarded-Host", incoming.hostname);
    headers.set("X-Forwarded-Proto", "https");
    headers.set("User-Agent", request.headers.get("User-Agent") || "LoungeTVOnline/2.0");

    if (playbackRoute) {
      headers.set("X-Lounge-Client-Key", clientKey(request));
    }

    const response = await fetch(new Request(upstream, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
      redirect: "manual",
    }));

    const out = new Headers(response.headers);
    out.set("X-Lounge-Service", "Universal-Catalogue-Staging");
    out.set("X-Content-Type-Options", "nosniff");
    out.delete("location");

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: out,
    });
  },
};