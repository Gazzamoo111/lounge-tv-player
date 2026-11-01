const UPSTREAM = "https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream";

export default {
  async fetch(request) {
    const incoming = new URL(request.url);

    if (incoming.pathname === "/health") {
      return Response.json(
        { ok: true, service: "LoungeTVOnline" },
        { headers: { "Cache-Control": "no-store" } },
      );
    }

    const allowed =
      incoming.pathname === "/" ||
      incoming.pathname === "/player_api.php" ||
      incoming.pathname === "/xmltv.php" ||
      incoming.pathname.startsWith("/live/") ||
      incoming.pathname.startsWith("/movie/") ||
      incoming.pathname.startsWith("/series/");

    if (!allowed) {
      return new Response("Not found", { status: 404 });
    }

    const upstream = new URL(UPSTREAM + (incoming.pathname === "/" ? "/player_api.php" : incoming.pathname));
    upstream.search = incoming.search;

    const headers = new Headers(request.headers);
    headers.set("X-Forwarded-Host", incoming.hostname);
    headers.set("X-Forwarded-Proto", "https");
    headers.set("User-Agent", "LoungeTVOnline/1.0");

    const response = await fetch(
      new Request(upstream, {
        method: request.method,
        headers,
        body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
        redirect: "manual",
      }),
    );

    const out = new Headers(response.headers);
    out.set("X-Lounge-Service", "LoungeTVOnline");

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: out,
    });
  },
};
