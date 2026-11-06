import type { Programme } from "./model";
export function xmltvTime(raw: string): number {
  const m = raw
    .trim()
    .match(
      /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*(?:([+-])(\d{2})(\d{2}))?$/,
    );
  if (!m) return NaN;
  const offset =
    (Number(m[8] ?? 0) * 60 + Number(m[9] ?? 0)) * (m[7] === "-" ? -1 : 1);
  return (
    Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] ?? 0)) -
    offset * 60000
  );
}
export function xmlText(raw: string): string {
  return raw
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&#x([a-f0-9]+);/gi, (_, v) =>
      String.fromCodePoint(parseInt(v, 16)),
    )
    .replace(/&#(\d+);/g, (_, v) => String.fromCodePoint(Number(v)))
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
}
export function programmeFromXml(
  xml: string,
): { channel: string; programme: Programme } | undefined {
  const attr = (name: string) =>
    xml.match(new RegExp("\\b" + name + "=[\"\\']([^\"\\']*)[\"\\']"))?.[1] ??
    "";
  const field = (name: string) =>
    xmlText(
      xml.match(
        new RegExp("<" + name + "(?:\\s[^>]*)?>([\\s\\S]*?)<\\/" + name + ">"),
      )?.[1] ?? "",
    );
  const start = xmltvTime(attr("start")),
    end = xmltvTime(attr("stop")),
    channel = xmlText(attr("channel"));
  if (
    !channel ||
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    end <= start
  )
    return;
  return {
    channel,
    programme: {
      id: channel + ":" + start,
      title: field("title"),
      description: field("desc"),
      start,
      end,
    },
  };
}
/** Reads programme records even when an XML tag is split across network chunks. */
export class XmltvStream {
  private buffer = "";
  feed(chunk: string, onProgramme: (xml: string) => void) {
    this.buffer += chunk;
    while (true) {
      const start = this.buffer.indexOf("<programme");
      if (start < 0) {
        this.buffer = this.buffer.slice(-20);
        return;
      }
      const end = this.buffer.indexOf("</programme>", start);
      if (end < 0) {
        this.buffer = this.buffer.slice(start);
        if (this.buffer.length > 2_000_000)
          throw Error("Invalid programme guide record");
        return;
      }
      onProgramme(this.buffer.slice(start, end + 12));
      this.buffer = this.buffer.slice(end + 12);
    }
  }
}
