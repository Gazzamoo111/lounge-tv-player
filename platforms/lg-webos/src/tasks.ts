/** Bounded metadata work; playback never uses this queue. Results retain input order. */
export async function mapLimit<T, R>(
  items: T[],
  limit: number,
  work: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const out: R[] = new Array(items.length);
  let cursor = 0;
  await Promise.all(
    Array.from(
      { length: Math.min(Math.max(1, limit), items.length) },
      async () => {
        while (cursor < items.length) {
          const i = cursor++;
          out[i] = await work(items[i], i);
        }
      },
    ),
  );
  return out;
}
