import { XmltvStream, programmeFromXml } from "./xmltv";
import type { Programme } from "./model";
const scope = globalThis as any;
scope.onmessage = async ({
  data,
}: {
  data: { url: string; ids: string[]; now: number };
}) => {
  const controller = new AbortController(),
    timeout = setTimeout(() => controller.abort(), 60000);
  try {
    const response = await fetch(data.url, {
      signal: controller.signal,
      credentials: "omit",
      cache: "no-store",
    });
    if (!response.ok || !response.body)
      throw Error("Programme guide unavailable");
    const reader = response.body.getReader(),
      decoder = new TextDecoder(),
      parser = new XmltvStream(),
      allowed = new Set(data.ids),
      rows = new Map<string, Programme[]>();
    let records = 0,
      last = 0,
      bytes = 0,
      tail = "";
    const collect = (xml: string) => {
      const entry = programmeFromXml(xml);
      if (
        !entry ||
        !allowed.has(entry.channel) ||
        entry.programme.end < data.now - 6 * 3600000 ||
        entry.programme.start > data.now + 8 * 86400000
      )
        return;
      const list = rows.get(entry.channel) ?? [];
      list.push(entry.programme);
      rows.set(entry.channel, list);
      records++;
    };
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      bytes += part.value.byteLength;
      if (bytes > 150_000_000)
        throw Error("Programme guide exceeds the TV cache limit");
      const chunk = decoder.decode(part.value, { stream: true });
      tail = (tail + chunk).slice(-1000);
      parser.feed(chunk, collect);
      if (Date.now() - last > 250) {
        last = Date.now();
        scope.postMessage({ type: "progress", records, channels: rows.size });
      }
    }
    parser.feed(decoder.decode(), collect);
    if (!tail.includes("</tv>")) throw Error("Incomplete programme guide");
    if (!records) throw Error("No programme entries were available");
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
    scope.postMessage({ type: "complete", records, channels: rows.size });
  } catch {
    scope.postMessage({
      type: "error",
      message: "The full programme guide could not be loaded.",
    });
  } finally {
    clearTimeout(timeout);
  }
};
