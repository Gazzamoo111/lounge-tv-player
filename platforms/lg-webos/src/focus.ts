export type Direction = "left" | "right" | "up" | "down";
export interface Rect {
  left: number;
  top: number;
  width: number;
  height: number;
}
export function nearest(
  from: Rect,
  candidates: { id: string; rect: Rect }[],
  dir: Direction,
): string | undefined {
  const fx = from.left + from.width / 2,
    fy = from.top + from.height / 2;
  return candidates
    .map((c) => {
      const dx = c.rect.left + c.rect.width / 2 - fx,
        dy = c.rect.top + c.rect.height / 2 - fy;
      const primary =
        dir === "left" ? -dx : dir === "right" ? dx : dir === "up" ? -dy : dy;
      const cross =
        dir === "left" || dir === "right" ? Math.abs(dy) : Math.abs(dx);
      return { ...c, primary, score: primary + cross * 3 };
    })
    .filter((c) => c.primary > 4)
    .sort((a, b) => a.score - b.score)[0]?.id;
}
export class FocusController {
  root: HTMLElement;
  lastContent = "";
  onBack = () => {};
  onMedia = (code: number) => false;
  onLong = (el: HTMLElement) => false;
  onMove: (el: HTMLElement, dir: Direction) => boolean = () => false;
  private timer?: ReturnType<typeof setTimeout>;
  private held = false;
  constructor(root: HTMLElement) {
    this.root = root;
    document.addEventListener("keydown", (e) => this.key(e));
    document.addEventListener("keyup", (e) => {
      if (e.key === "Enter" || e.keyCode === 13) {
        clearTimeout(this.timer);
        this.timer = undefined;
        if (
          !this.held &&
          (document.activeElement as HTMLElement)?.tagName !== "INPUT"
        )
          (document.activeElement as HTMLElement)?.click();
        this.held = false;
      }
    });
    document.addEventListener("focusin", (e) => {
      const el = e.target as HTMLElement;
      if (
        el.id &&
        !el.closest("nav") &&
        !el.closest("#overlay") &&
        !el.closest("#player")
      )
        this.lastContent = el.id;
    });
  }
  private key(e: KeyboardEvent) {
    const code = e.keyCode;
    if ([461, 10009, 27].includes(code) || e.key === "Escape") {
      e.preventDefault();
      this.onBack();
      return;
    }
    if (this.onMedia(code)) {
      e.preventDefault();
      return;
    }
    const el = document.activeElement as HTMLElement;
    if (e.key === "Tab") {
      e.preventDefault();
      return;
    }
    if (e.key === "Enter" || code === 13) {
      if (el?.tagName === "INPUT") return;
      e.preventDefault();
      if (!e.repeat && !this.timer) {
        this.held = false;
        this.timer = setTimeout(() => {
          this.timer = undefined;
          this.held = this.onLong(el);
        }, 650);
      }
      return;
    }
    const dir = (
      { 37: "left", 38: "up", 39: "right", 40: "down" } as Record<
        number,
        Direction
      >
    )[code];
    if (!dir) return;
    if (el?.tagName === "INPUT" && ["left", "right"].includes(dir)) return;
    e.preventDefault();
    if (!el) return;
    if (this.onMove(el, dir)) return;
    const explicit = el.dataset[dir];
    if (explicit) {
      this.focus(explicit);
      return;
    }
    const scope = el.closest("[data-focus-scope]") ?? this.root;
    const list = Array.from(
      scope.querySelectorAll<HTMLElement>("[data-focus]"),
    ).filter(
      (x) =>
        x !== el &&
        !x.hidden &&
        x.getClientRects().length &&
        !x.closest("[hidden]"),
    );
    const id = nearest(
      el.getBoundingClientRect(),
      list.map((x) => ({ id: x.id, rect: x.getBoundingClientRect() })),
      dir,
    );
    if (id) this.focus(id);
  }
  focus(id: string, scroll = true) {
    const el = document.getElementById(id);
    if (!el || el.hidden) return false;
    el.focus({ preventScroll: true });
    if (scroll) el.scrollIntoView({ block: "nearest", inline: "nearest" });
    return true;
  }
}
export class VirtualCollection {
  private start = -1;
  private end = -1;
  private observer: () => void;
  private cells = new Map<number, HTMLElement>();
  private scheduled = 0;
  constructor(
    public host: HTMLElement,
    public count: number,
    public columns: number,
    public rowHeight: number,
    private draw: (index: number) => HTMLElement,
  ) {
    host.classList.add("virtual");
    if (getComputedStyle(host).position === "static")
      host.style.position = "relative";
    const spacer = document.createElement("div");
    spacer.className = "spacer";
    spacer.style.height = `${Math.ceil(count / columns) * rowHeight}px`;
    host.append(spacer);
    this.observer = () => {
      if (!this.scheduled)
        this.scheduled = requestAnimationFrame(() => {
          this.scheduled = 0;
          this.render();
        });
    };
    host.addEventListener("scroll", this.observer);
    this.render();
  }
  render(force = false) {
    const start =
        Math.max(0, Math.floor(this.host.scrollTop / this.rowHeight) - 1) *
        this.columns,
      end = Math.min(
        this.count,
        (Math.ceil(
          (this.host.scrollTop + this.host.clientHeight) / this.rowHeight,
        ) +
          2) *
          this.columns,
      );
    if (!force && start === this.start && end === this.end) return;
    this.start = start;
    this.end = end;
    const active = document.activeElement?.id;
    for (const [index, cell] of this.cells)
      if (index < start || index >= end) {
        cell.remove();
        this.cells.delete(index);
      }
    for (let i = start; i < end; i++) {
      if (this.cells.has(i)) continue;
      const cell = this.draw(i);
      cell.classList.add("virtual-cell");
      cell.style.position = "absolute";
      cell.style.top = `${Math.floor(i / this.columns) * this.rowHeight}px`;
      cell.style.left = `${((i % this.columns) * 100) / this.columns}%`;
      cell.style.width = `${100 / this.columns}%`;
      cell.dataset.index = String(i);
      cell.dataset.collection = this.host.id;
      this.cells.set(i, cell);
      this.host.append(cell);
    }
    if (active) document.getElementById(active)?.focus({ preventScroll: true });
  }
  focus(index: number) {
    if (index < 0 || index >= this.count) return false;
    const top = Math.floor(index / this.columns) * this.rowHeight;
    if (top < this.host.scrollTop) this.host.scrollTop = top;
    else if (
      top + this.rowHeight >
      this.host.scrollTop + this.host.clientHeight
    )
      this.host.scrollTop = top + this.rowHeight - this.host.clientHeight;
    this.render();
    this.host
      .querySelector<HTMLElement>(`[data-index="${index}"]`)
      ?.focus({ preventScroll: true });
    return true;
  }
  move(el: HTMLElement, dir: Direction) {
    const i = Number(el.dataset.index);
    let n = i;
    if (dir === "left") {
      if (i % this.columns === 0) return false;
      n--;
    }
    if (dir === "right") {
      if (i % this.columns === this.columns - 1) return false;
      n++;
    }
    if (dir === "up") n -= this.columns;
    if (dir === "down") n += this.columns;
    return this.focus(n);
  }
  destroy() {
    this.host.removeEventListener("scroll", this.observer);
    if (this.scheduled) cancelAnimationFrame(this.scheduled);
    this.cells.clear();
  }
}
