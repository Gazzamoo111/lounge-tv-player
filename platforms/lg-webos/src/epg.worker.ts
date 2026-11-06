import { mergeGuide, hasCurrentGuide } from "./guide-policy";
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
    sources?: { url: string; id?: string; name?: string; regions?: string[] }[];
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
    const channels =
      data.channels ??
      data.ids.map((id) => ({ id, epgId: id, name: "" }) as GuideChannel);
    const rows = new Map<string, Programme[]>();
    let records = 0,
      last = 0,
      bytes = 0,
      succeeded = 0,
      failed = 0;
    const sourceStats: {
      id: string;
      name: string;
      status: string;
      currentChannels: number;
      records: number;
    }[] = [];
    const sources =
      data.sources ??
      (data.urls ?? [data.url]).map((url, i) => ({
        url,
        id: String(i),
        name: "Programme guide",
        regions: undefined,
      }));
    for (const [sourceIndex, source] of sources.entries()) {
      const matcher = new GuideMatcher(
        source.regions?.length
          ? channels.filter(
              (c) => !!c.region && source.regions!.includes(c.region),
            )
          : channels,
        data.overrides,
      );
      const feedController = new AbortController(),
        abort = () => feedController.abort();
      controller.signal.addEventListener("abort", abort, { once: true });
      const feedTimer = setTimeout(abort, sourceIndex === 0 ? 45000 : 20000);
      try {
        const response = await fetch(source.url, {
          signal: feedController.signal,
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
          const id = xmlText(
            xml
              .slice(0, xml.indexOf(">") + 1)
              .match(/\bchannel\s*=\s*["']([^"']*)["']/)?.[1] ?? "",
          );
          const targets = matcher.targets(id);
          if (!targets.length) return;
          const entry = programmeFromXml(xml);
          if (
            !entry ||
            entry.programme.end < data.now - 6 * 3600000 ||
            entry.programme.start > data.now + (data.days ?? 7) * 86400000
          )
            return;
          for (const target of targets) {
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
          const merged = mergeGuide(existing, list, data.now);
          records += merged.length - existing.length;
          rows.set(id, merged);
        }
        sourceStats.push({
          id: source.id ?? String(sourceIndex),
          name: source.name ?? "Programme guide",
          status: "ready",
          currentChannels: [...sourceRows.values()].filter((r) =>
            hasCurrentGuide(r, data.now),
          ).length,
          records: [...sourceRows.values()].reduce((n, r) => n + r.length, 0),
        });
        succeeded++;
      } catch {
        failed++;
        sourceStats.push({
          id: source.id ?? String(sourceIndex),
          name: source.name ?? "Programme guide",
          status: "unavailable",
          currentChannels: 0,
          records: 0,
        });
        scope.postMessage({ type: "source-error", sourceIndex });
      } finally {
        clearTimeout(feedTimer);
        controller.signal.removeEventListener("abort", abort);
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
      currentChannels: [...rows.values()].filter((r) =>
        hasCurrentGuide(r, data.now),
      ).length,
      totalChannels: channels.length,
      sourceStats,
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
