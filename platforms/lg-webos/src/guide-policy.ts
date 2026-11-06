import type { Programme } from "./model";
export function hasCurrentGuide(
  rows: Programme[] | undefined,
  now = Date.now(),
) {
  return !!rows?.some((p) => p.title.trim() && p.start <= now && p.end > now);
}
/** Prefer supplied rows only where their actual time interval is present. Never invent gaps. */
export function mergeGuide(
  primary: Programme[],
  fallback: Programme[],
  now = Date.now(),
) {
  const clean = (rows: Programme[]) =>
    rows.filter(
      (p) =>
        p.title.trim() &&
        Number.isFinite(p.start) &&
        Number.isFinite(p.end) &&
        p.end > p.start &&
        p.end > now - 6 * 3600000,
    );
  const first = clean(primary),
    extra = clean(fallback).filter(
      (p) => !first.some((e) => p.start < e.end && p.end > e.start),
    );
  const seen = new Set<string>();
  return [...first, ...extra]
    .sort((a, b) => a.start - b.start)
    .filter((p) => {
      const k = p.start + ":" + p.end + ":" + p.title;
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });
}
