import { Media, title } from "./model";
export interface TrendingCandidate {
  id: number;
  title?: string;
  name?: string;
  original_title?: string;
  original_name?: string;
  release_date?: string;
  first_air_date?: string;
  poster_path?: string;
  backdrop_path?: string;
  vote_average: number;
  overview: string;
}
export const signature = (s: string) =>
  title(s)
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\b(19|20)\d{2}\b/g, "")
    .replace(/\b(4k|uhd|fhd|hd|sd|1080p|720p|2160p|h264|h265|hevc|avc)\b/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
export function exactPicks(rows: Media[], names: string[], limit = 18) {
  const out: Media[] = [];
  for (const name of names) {
    const matches = rows.filter(
      (m) => signature(m.name) === signature(name) && m.image,
    );
    if (matches.length) out.push(matches[0]);
    if (out.length === limit) break;
  }
  return out;
}
export function matchTrending(
  rows: Media[],
  candidates: TrendingCandidate[],
): Media[] {
  const out: Media[] = [];
  for (const c of candidates) {
    const names = [c.title, c.name, c.original_title, c.original_name]
      .filter(Boolean)
      .map((x) => signature(x!));
    const year = (c.release_date ?? c.first_air_date ?? "").slice(0, 4);
    const matches = rows.filter(
      (m) =>
        names.includes(signature(m.name)) &&
        (!m.year || !year || m.year === year),
    );
    if (matches.length !== 1) continue;
    const m = matches[0];
    if (out.some((x) => x.id === m.id)) continue;
    out.push({
      ...m,
      image: c.poster_path
        ? "https://image.tmdb.org/t/p/w500" + c.poster_path
        : m.image,
      backdrop: c.backdrop_path
        ? "https://image.tmdb.org/t/p/w1280" + c.backdrop_path
        : m.backdrop,
      rating: c.vote_average,
      plot: c.overview,
      year,
    });
  }
  return out;
}
export function assembleTrending(movies: Media[], series: Media[]) {
  const s = series.slice(0, 10),
    m = movies.slice(0, 10 - s.length),
    out: Media[] = [];
  for (let i = 0; i < Math.max(s.length, m.length); i++) {
    if (m[i]) out.push(m[i]);
    if (s[i]) out.push(s[i]);
  }
  return out.slice(0, 10);
}
