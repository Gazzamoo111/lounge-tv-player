/** Forward the finished guide with backpressure, retaining only a tiny closing tail and ID sets. */
export function appendXmltv(
  input: ReadableStream<Uint8Array>,
  supplement: (presence: string) => Promise<string>,
): ReadableStream<Uint8Array> {
  const reader = input.getReader();
  const decoder = new TextDecoder();
  const encoder = new TextEncoder();
  const channels = new Set<string>();
  const programmes = new Set<string>();
  let tail = "", scanTail = "", done = false;
  function scan(text: string) {
    const part = scanTail + text;
    for (const m of part.matchAll(/<(channel|programme)\b[^>]*>/g)) {
      const id = m[0].match(m[1] === "channel" ? /\bid\s*=\s*["']([^"']+)["']/ : /\bchannel\s*=\s*["']([^"']+)["']/)?.[1];
      if (id) (m[1] === "channel" ? channels : programmes).add(id);
    }
    scanTail = part.slice(-4096);
    if (channels.size > 20000 || programmes.size > 20000) throw Error("Too many guide identities");
  }
  return new ReadableStream<Uint8Array>({
    async pull(controller) {
      if (done) return;
      try {
        for (;;) {
          const next = await reader.read();
          const text = next.done ? decoder.decode() : decoder.decode(next.value, { stream: true });
          scan(text); tail += text;
          if (next.done) {
            done = true;
            if (!/<\/tv>\s*$/i.test(tail)) throw Error("Incomplete upstream XMLTV guide");
            const presence = [...channels].map(id => `<channel id="${id}"/>`).join("") +
              [...programmes].map(id => `<programme channel="${id}"/>`).join("");
            const extra = await supplement(presence);
            controller.enqueue(encoder.encode(tail.replace(/<\/tv>\s*$/i, "") + extra + "</tv>\n"));
            controller.close(); reader.releaseLock(); return;
          }
          if (tail.length > 512) {
            const ready = tail.slice(0, -512); tail = tail.slice(-512);
            controller.enqueue(encoder.encode(ready)); return;
          }
        }
      } catch (error) {
        done = true;
        await reader.cancel(error).catch(() => {});
        controller.error(error);
      }
    },
    async cancel(reason) { done = true; await reader.cancel(reason); },
  }, { highWaterMark: 1 });
}
