import {
  Credentials,
  Media,
  Profile,
  Progress,
  SportEvent,
  key,
} from "./model";
export interface Store {
  getItem(k: string): string | null;
  setItem(k: string, v: string): void;
  removeItem(k: string): void;
}
export class LibraryState {
  private account = "";
  private memory = new Map<string, unknown>();
  profile = "default";
  constructor(private store: Store) {}
  scope(username: string) {
    this.account = username;
    this.profile = this.read<string>("active-profile", "default");
  }
  private id(k: string) {
    return `lounge.webos.v1:${encodeURIComponent(this.account)}:${k}`;
  }
  read<T>(k: string, fallback: T): T {
    const id = this.id(k);
    if (this.memory.has(id)) return this.memory.get(id) as T;
    try {
      const value = JSON.parse(this.store.getItem(id) ?? "null") ?? fallback;
      this.memory.set(id, value);
      return value;
    } catch {
      return fallback;
    }
  }
  write(k: string, v: unknown) {
    try {
      this.store.setItem(this.id(k), JSON.stringify(v));
      this.memory.set(this.id(k), v);
    } catch {
      throw new Error(
        "TV storage is full. Your latest change could not be saved.",
      );
    }
  }
  profiles(): Profile[] {
    return this.read("profiles", [{ id: "default", name: "Home" }]);
  }
  addProfile(name: string) {
    const p = { id: `p${Date.now()}`, name: name.trim().slice(0, 24) };
    if (!p.name) return;
    const rows = this.profiles();
    if (rows.length >= 8)
      throw Error("You can have up to eight profiles on this TV.");
    this.write("profiles", [...rows, p]);
    this.switchProfile(p.id);
  }
  switchProfile(id: string) {
    if (!this.profiles().some((p) => p.id === id))
      throw Error("Profile unavailable");
    this.write("active-profile", id);
    this.profile = id;
  }
  get profileName() {
    return this.profiles().find((p) => p.id === this.profile)?.name ?? "Home";
  }
  favourites(): Media[] {
    return this.read(`${this.profile}:favourites`, []);
  }
  saved(m: Media) {
    return this.favourites().some((x) => key(x) === key(m));
  }
  toggle(m: Media) {
    const list = this.favourites();
    const has = list.some((x) => key(x) === key(m));
    this.write(
      `${this.profile}:favourites`,
      has ? list.filter((x) => key(x) !== key(m)) : [...list, m],
    );
    return !has;
  }
  progress(): Progress[] {
    return this.read(`${this.profile}:progress`, []);
  }
  progressFor(m: Media) {
    return this.progress().find((p) => key(p.media) === key(m));
  }
  updateProgress(media: Media, position: number, duration: number) {
    if (media.kind === "live" || !Number.isFinite(position) || position < 0)
      return;
    const rows = this.progress().filter((p) => key(p.media) !== key(media));
    const complete = duration > 0 && position >= duration * 0.95;
    this.write(
      `${this.profile}:progress`,
      [
        { media, position, duration, updated: Date.now(), complete },
        ...rows,
      ].slice(0, 250),
    );
  }
  continuing() {
    return this.progress()
      .filter((p) => !p.complete && p.position > 15)
      .sort((a, b) => b.updated - a.updated);
  }
  reminders(): SportEvent[] {
    return this.read(`${this.profile}:reminders`, []);
  }
  toggleReminder(e: SportEvent) {
    const rows = this.reminders(),
      has = rows.some((x) => x.id === e.id);
    this.write(
      `${this.profile}:reminders`,
      has ? rows.filter((x) => x.id !== e.id) : [...rows, e],
    );
    return !has;
  }
  teams(): string[] {
    return this.read(`${this.profile}:teams`, []);
  }
  toggleTeam(id: string) {
    const rows = this.teams();
    this.write(
      `${this.profile}:teams`,
      rows.includes(id) ? rows.filter((x) => x !== id) : [...rows, id],
    );
  }
  settings() {
    return this.read("settings", {
      autoplayNext: true,
      subtitles: false,
      aspect: "contain",
      rememberLogin: true,
    });
  }
  setSettings(value: ReturnType<LibraryState["settings"]>) {
    this.write("settings", value);
  }
  clearHistory() {
    this.write(`${this.profile}:progress`, []);
  }
  remember(auth: Credentials | null) {
    if (auth) this.store.setItem("lounge.webos.login", JSON.stringify(auth));
    else this.store.removeItem("lounge.webos.login");
  }
  credentials(): Credentials | null {
    try {
      const a = JSON.parse(this.store.getItem("lounge.webos.login") ?? "null");
      return typeof a?.username === "string" && typeof a?.password === "string"
        ? a
        : null;
    } catch {
      return null;
    }
  }
}
