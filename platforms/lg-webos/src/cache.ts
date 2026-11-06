import { Category, Kind, Media, Programme } from "./model";
export interface CachedCatalogue {
  at: number;
  categories: Partial<Record<Kind, Category[]>>;
  catalogues: Partial<Record<Kind, Media[]>>;
}
export interface CachedGuide {
  at: number;
  rows: Programme[];
}
/** Device-local cache. Never contains passwords or provider configuration. */
export class DeviceCache {
  private opening?: Promise<IDBDatabase>;
  private open() {
    return (this.opening ??= new Promise<IDBDatabase>((resolve, reject) => {
      const req = indexedDB.open("lounge-tv-device-cache", 1);
      req.onupgradeneeded = () => req.result.createObjectStore("entries");
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => {
        this.opening = undefined;
        reject(Error("TV cache unavailable"));
      };
      req.onblocked = () => reject(Error("TV cache is busy"));
    }));
  }
  async get<T>(id: string): Promise<T | undefined> {
    try {
      const db = await this.open();
      return await new Promise<T | undefined>((resolve, reject) => {
        const tx = db.transaction("entries", "readonly"),
          req = tx.objectStore("entries").get(id);
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      });
    } catch {
      return undefined;
    }
  }
  async put(id: string, value: unknown) {
    try {
      const db = await this.open();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction("entries", "readwrite");
        tx.objectStore("entries").put(value, id);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
        tx.onabort = () => reject(tx.error);
      });
    } catch {
      /* Storage failure must not prevent a customer from watching. */
    }
  }
  async putGuideBatch(
    account: string,
    generation: string,
    rows: [string, Programme[]][],
    at: number,
  ) {
    try {
      const db = await this.open();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction("entries", "readwrite"),
          store = tx.objectStore("entries");
        for (const [id, programmes] of rows)
          store.put(
            { at, rows: programmes },
            `${account}:xmltv:${generation}:${id}`,
          );
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
        tx.onabort = () => reject(tx.error);
      });
    } catch {
      throw Error("Unable to cache the programme guide on this TV.");
    }
  }
  async pruneGuides(account: string, keep: string) {
    try {
      const db = await this.open();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction("entries", "readwrite"),
          req = tx
            .objectStore("entries")
            .openCursor(
              IDBKeyRange.bound(account + ":xmltv:", account + ":xmltv:\uffff"),
            );
        req.onsuccess = () => {
          const cursor = req.result;
          if (cursor) {
            if (
              !String(cursor.key).startsWith(account + ":xmltv:" + keep + ":")
            )
              cursor.delete();
            cursor.continue();
          }
        };
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
    } catch {
      /* Old guide generations may be cleared on the next successful refresh. */
    }
  }
  catalogue(account: string) {
    return this.get<CachedCatalogue>(account + ":catalogue");
  }
  saveCatalogue(account: string, data: CachedCatalogue) {
    return this.put(account + ":catalogue", data);
  }
  guide(account: string, id: string) {
    return this.get<CachedGuide>(account + ":short-guide:" + id);
  }
  saveGuide(account: string, id: string, data: CachedGuide) {
    return this.put(account + ":short-guide:" + id, data);
  }
}
export function completeCatalogue(
  data: CachedCatalogue | undefined,
): data is CachedCatalogue {
  return (
    !!data &&
    ["live", "movie", "series"].every(
      (k) =>
        Array.isArray(data.categories[k as Kind]) &&
        Array.isArray(data.catalogues[k as Kind]),
    )
  );
}
