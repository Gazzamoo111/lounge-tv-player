import { GuideMatcher, GuideChannel } from "./guide-match";
import { XmltvStream, programmeFromXml, xmlText } from "./xmltv";
import type { Programme } from "./model";
const scope = globalThis as any;
scope.onmessage = async ({
  data,
}: {
  data: {
    url: string;
    urls?: string[];
    ids: string[];
    channels?: GuideChannel[];
    overrides?: Record<string, string>;
    now: number;
    days?: number;
  };
}) => {
  const controller = new AbortController(),
    timeout = setTimeout(() => controller.abort(), 120000);
  try {
    const matcher = new GuideMatcher(
      data.channels ?? data.ids.map((id) => ({ id, epgId: id, name: "" })),
      data.overrides,
    );
    const rows = new Map<string, Programme[]>();
    let records = 0,
      last = 0,
      bytes = 0,
      succeeded = 0,
      failed = 0;
    for (const [sourceIndex, url] of (data.urls ?? [data.url]).entries()) {
      try {
        const response = await fetch(url, {
          signal: controller.signal,
          credentials: "omit",
          cache: "no-store",
        });
        if (!response.ok || !response.body) throw Error("Guide unavailable");
        const encoded = response.body.getReader(),
          first = await encoded.read();
        let body = new ReadableStream<Uint8Array>({
          start(c) {
            if (!first.done) c.enqueue(first.value);
          },
          async pull(c) {
            const next = await encoded.read();
            if (next.done) c.close();
            else c.enqueue(next.value);
          },
          cancel(reason) {
            return encoded.cancel(reason);
          },
        });
        if (!first.done && first.value[0] === 0x1f && first.value[1] === 0x8b) {
          const Gzip = (globalThis as any).DecompressionStream;
          if (!Gzip)
            throw Error(
              "Gzip is unavailable on this TV; use a plain XML source.",
            );
          body = body.pipeThrough(new Gzip("gzip"));
        }
        const reader = body.getReader(),
          decoder = new TextDecoder(),
          parser = new XmltvStream();
        // Each source commits only when the XML closes cleanly. First source wins overlaps.
        const sourceRows = new Map<string, Programme[]>();
        let tail = "";
        const collect = (xml: string) => {
          const entry = programmeFromXml(xml);
          if (
            !entry ||
            entry.programme.end < data.now - 6 * 3600000 ||
            entry.programme.start > data.now + (data.days ?? 7) * 86400000
          )
            return;
          for (const target of matcher.targets(entry.channel)) {
            const list = sourceRows.get(target) ?? [];
            list.push(entry.programme);
            sourceRows.set(target, list);
          }
        };
        const channel = (xml: string) => {
          const id = xmlText(xml.match(/\bid=["']([^"']+)["']/)?.[1] ?? "");
          const names = [
            ...xml.matchAll(
              /<display-name(?:\s[^>]*)?>([\s\S]*?)<\/display-name>/g,
            ),
          ].map((m) => xmlText(m[1]));
          matcher.register(id, names);
        };
        while (true) {
          const part = await reader.read();
          if (part.done) break;
          bytes += part.value.byteLength;
          if (bytes > 250_000_000)
            throw Error("Programme guide exceeds TV cache limit");
          const chunk = decoder.decode(part.value, { stream: true });
          tail = (tail + chunk).slice(-1000);
          parser.feed(chunk, collect, channel);
          if (Date.now() - last > 500) {
            last = Date.now();
            scope.postMessage({
              type: "progress",
              records:
                records +
                [...sourceRows.values()].reduce((n, r) => n + r.length, 0),
              channels: sourceRows.size,
            });
          }
        }
        parser.feed(decoder.decode(), collect, channel);
        if (!tail.includes("</tv>")) throw Error("Incomplete guide");
        for (const [id, list] of sourceRows) {
          const existing = rows.get(id) ?? [];
          const fresh = list.filter(
            (p) => !existing.some((e) => p.start < e.end && p.end > e.start),
          );
          rows.set(id, [...existing, ...fresh]);
          records += fresh.length;
        }
        succeeded++;
      } catch {
        failed++;
        scope.postMessage({ type: "source-error", sourceIndex });
      }
    }
    if (!succeeded || !records) throw Error("No programme entries available");
    let batch: [string, Programme[]][] = [];
    for (const [id, list] of rows) {
      list.sort((a, b) => a.start - b.start);
      const seen = new Set<string>();
      batch.push([id, list.filter((p) => !seen.has(p.id) && !!seen.add(p.id))]);
      if (batch.length === 100) {
        scope.postMessage({ type: "batch", rows: batch });
        batch = [];
      }
    }
    if (batch.length) scope.postMessage({ type: "batch", rows: batch });
    scope.postMessage({
      type: "complete",
      records,
      channels: rows.size,
      failed,
    });
  } catch {
    scope.postMessage({
      type: "error",
      message: "The full programme guide could not be loaded.",
    });
  } finally {
    clearTimeout(timeout);
  }
};
