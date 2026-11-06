import type { Media, Programme } from "./model";
import { nzTime } from "./model";
import { VirtualCollection, Direction } from "./focus";
import { button, node, image } from "./ui";
export interface GuideGridOptions {
  channels: Media[];
  start: number;
  read: (id: string) => Promise<Programme[]>;
  watch: (m: Media) => void;
  focus: (id: string) => boolean;
  info: (m: Media, p?: Programme) => void;
  leave: () => void;
  toolbar: () => void;
  shift: (delta: number, index: number, time: number) => void;
}
export class GuideGrid {
  collection: VirtualCollection;
  private wantedTime: number;
  private cursor = 0;
  constructor(
    public host: HTMLElement,
    private options: GuideGridOptions,
  ) {
    this.wantedTime = Math.max(options.start, Date.now());
    this.collection = new VirtualCollection(
      host,
      options.channels.length,
      1,
      66,
      (i) => this.draw(i),
    );
  }
  private draw(index: number) {
    const ch = this.options.channels[index],
      row = node("div", "guide-row");
    row.dataset.guideIndex = String(index);
    row.dataset.guideChannel = ch.id;
    const label = button(
      "guide-channel-" + index,
      ch.name,
      () => this.options.watch(ch),
      "guide-channel",
    );
    label.dataset.guideRow = String(index);
    label.dataset.guideColumn = "channel";
    label.append(image(ch.image, "channel-logo"));
    label.addEventListener("focus", () => {
      this.cursor = index;
      void this.options.read(ch.id);
      this.options.info(ch);
    });
    const cells = node("div", "guide-programmes");
    row.append(label, cells);
    this.cells(index, cells, []);
    void this.options.read(ch.id).then(
      (rows) => {
        if (!row.isConnected) return;
        this.cells(index, cells, rows);
      },
      () => {
        if (row.isConnected) this.cells(index, cells, []);
      },
    );
    return row;
  }
  private cells(index: number, host: HTMLElement, rows: Programme[]) {
    const active = document.activeElement as HTMLElement,
      hadFocus = host.contains(active),
      wanted = this.wantedTime;
    const visible = rows.filter(
      (p) =>
        p.end > this.options.start && p.start < this.options.start + 7200000,
    );
    host.replaceChildren();
    const ch = this.options.channels[index];
    if (!visible.length) {
      const b = button(
        `guide-cell-${index}-0`,
        "No programme information",
        () => this.options.watch(ch),
        "guide-cell",
      );
      b.style.width = "100%";
      b.dataset.guideRow = String(index);
      b.dataset.guideTime = String(this.options.start);
      b.dataset.guideEnd = String(this.options.start + 7200000);
      b.addEventListener("focus", () => {
        this.cursor = index;
        this.options.info(ch);
      });
      host.append(b);
    }
    visible.forEach((p, j) => {
      const b = button(
        `guide-cell-${index}-${j}`,
        "",
        () => this.options.watch(ch),
        "guide-cell" +
          (p.start <= Date.now() && p.end > Date.now() ? " current" : ""),
      );
      b.append(
        node("b", "", p.title),
        node("small", "muted", `${nzTime(p.start)} – ${nzTime(p.end)}`),
      );
      b.style.position = "absolute";
      b.style.left = `${Math.max(0, ((p.start - this.options.start) / 7200000) * 100)}%`;
      b.style.width = `${((Math.min(p.end, this.options.start + 7200000) - Math.max(p.start, this.options.start)) / 7200000) * 100}%`;
      b.dataset.guideRow = String(index);
      b.dataset.guideTime = String(p.start);
      b.dataset.guideEnd = String(p.end);
      b.addEventListener("focus", () => {
        this.cursor = index;
        if (this.wantedTime < p.start || this.wantedTime >= p.end)
          this.wantedTime = Math.max(this.options.start, p.start) + 1;
        void this.options.read(ch.id);
        this.options.info(ch, p);
      });
      host.append(b);
    });
    if (hadFocus) this.focus(index, wanted, false);
  }
  focus(index: number, time = this.wantedTime, channel = false) {
    if (index < 0 || index >= this.options.channels.length) return false;
    this.cursor = index;
    this.wantedTime = time;
    const row = this.collection.reveal(index);
    if (!row) return false;
    if (channel) return this.options.focus("guide-channel-" + index);
    const cells = [...row.querySelectorAll<HTMLElement>("[data-guide-time]")];
    const selected =
      cells.find(
        (b) =>
          Number(b.dataset.guideTime) <= time &&
          Number(b.dataset.guideEnd) > time,
      ) ??
      cells.reduce<HTMLElement | undefined>(
        (best, b) =>
          !best ||
          Math.abs(Number(b.dataset.guideTime) - time) <
            Math.abs(Number(best.dataset.guideTime) - time)
            ? b
            : best,
        undefined,
      );
    return selected ? this.options.focus(selected.id) : false;
  }
  move(el: HTMLElement, dir: Direction) {
    if (el.dataset.guideRow === undefined) return false;
    const index = Number(el.dataset.guideRow),
      channel = el.dataset.guideColumn === "channel";
    if (dir === "up" || dir === "down") {
      const next = index + (dir === "up" ? -1 : 1);
      if (next < 0) this.options.toolbar();
      else if (next < this.options.channels.length)
        this.focus(next, this.wantedTime, channel);
      return true;
    }
    if (dir === "left" && channel) {
      this.options.leave();
      return true;
    }
    if (dir === "right" && channel) {
      this.focus(index, this.wantedTime);
      return true;
    }
    const row = el.closest(".guide-row")!,
      cells = [...row.querySelectorAll<HTMLElement>("[data-guide-time]")],
      at = cells.indexOf(el),
      next = cells[at + (dir === "right" ? 1 : -1)];
    if (next) {
      this.wantedTime = Number(next.dataset.guideTime) + 1;
      this.options.focus(next.id);
    } else if (dir === "left") this.focus(index, this.wantedTime, true);
    else this.options.shift(7200000, index, this.options.start + 7200001);
    return true;
  }
  refresh() {
    for (const [index, row] of this.visibleRows()) {
      void this.options.read(this.options.channels[index].id).then((rows) => {
        if (row.isConnected)
          this.cells(index, row.querySelector(".guide-programmes")!, rows);
      });
    }
  }
  private visibleRows(): [number, HTMLElement][] {
    return [...this.host.querySelectorAll<HTMLElement>(".guide-row")].map(
      (row) => [Number(row.dataset.guideIndex), row],
    );
  }
  get index() {
    return this.cursor;
  }
  destroy() {
    this.collection.destroy();
  }
}
