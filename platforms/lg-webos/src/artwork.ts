/** Bound TMDB decode size on TV hardware. Leave other artwork hosts untouched. */
export function artworkUrl(url: string, backdrop = false) {
  try {
    const u = new URL(url);
    if (u.hostname === "image.tmdb.org")
      u.pathname = u.pathname.replace(
        /\/t\/p\/[^/]+\//,
        `/t/p/${backdrop ? "w780" : "w342"}/`,
      );
    return u.toString();
  } catch {
    return url;
  }
}
