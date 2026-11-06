import catalogue from "./settings-catalogue.json";
import { button, icon, input, node } from "./ui";
export interface SettingControl {
  value: string;
  run: () => void;
  help?: string;
  supported?: boolean;
}
export interface SettingsHost {
  control: (key: string) => SettingControl | undefined;
  focus: (id: string) => void;
  pins: () => string[];
  savePins: (keys: string[]) => void;
  menu: (key: string) => void;
}
export const SETTINGS_GROUPS = catalogue.groups;
export const SETTINGS_ROWS = catalogue.rows;
export class SettingsScreen {
  group = "";
  query = "";
  constructor(private host: SettingsHost) {}
  reset() {
    this.group = "";
    this.query = "";
  }
  back(main: HTMLElement) {
    if (!this.group && !this.query) return false;
    const previous = this.group;
    this.reset();
    this.render(main);
    this.host.focus("settings-group-" + previous);
    return true;
  }
  render(main: HTMLElement) {
    main.className = "settings stage-settings";
    main.replaceChildren();
    const group = catalogue.groups.find((g) => g.id === this.group);
    const title = node(
      "h1",
      "settings-title",
      group ? "Settings › " + group.title : "Settings",
    );
    main.append(title);
    const search = input("settings-search", "Search all settings");
    search.value = this.query;
    main.append(search);
    let timer: ReturnType<typeof setTimeout>;
    search.addEventListener("input", () => {
      clearTimeout(timer);
      timer = setTimeout(() => {
        this.query = search.value;
        this.render(main);
        this.host.focus("settings-search");
      }, 200);
    });
    if (!this.group && !this.query) {
      const menu = node("aside", "settings-menu");
      menu.append(node("h2", "", "Your lounge"));
      for (const [id, label] of [
        ["profiles", "Profiles"],
        ["playlists", "Playlists"],
        ["saved", "Favorites"],
        ["history", "History"],
        ["backup", "Backup & Restore"],
        ["diagnostics", "Error log"],
        ["about", "About"],
      ]) {
        const b = button(
          "settings-menu-" + id,
          label,
          () => this.host.control(id)?.run(),
          "settings-menu-row",
        );
        menu.append(b);
      }
      main.append(menu);
      const grid = node("section", "settings-group-grid");
      for (const g of catalogue.groups) {
        const b = button(
          "settings-group-" + g.id,
          "",
          () => {
            this.group = g.id;
            this.query = "";
            this.render(main);
            this.host.focus("setting-" + this.rows()[0]?.key);
          },
          "settings-group",
        );
        b.append(
          icon(g.icon),
          node("strong", "", g.title),
          node("small", "", g.description),
          node("span", "settings-count", String(this.rows(g.id).length)),
        );
        grid.append(b);
      }
      main.append(grid);
      return;
    }
    const rows = node("section", "settings-rows");
    rows.dataset.scroll = "";
    const panel = node("aside", "settings-help");
    main.append(rows, panel);
    for (const r of this.rows()) {
      const control = this.host.control(r.key),
        supported = control?.supported !== false && !!control;
      const b = button(
        "setting-" + r.key,
        "",
        () => {
          if (supported) {
            control!.run();
            if (!main.isConnected) return;
            const value = this.host.control(r.key)?.value;
            const v = b.querySelector(".setting-value");
            if (v) v.textContent = value ?? "";
            help();
          } else help();
        },
        "stage-setting" + (supported ? "" : " limited"),
      );
      b.dataset.setting = r.key;
      const copy = node("div", "setting-copy");
      copy.append(
        node("strong", "", r.title),
        node(
          "small",
          "",
          supported
            ? r.description.replace(/%\d+\$[sd]/g, "")
            : (control?.help ??
                "Android setting. Not available in this LG build."),
        ),
      );
      b.append(
        icon(catalogue.groups.find((g) => g.id === r.group)?.icon ?? "INFO"),
        copy,
        node("span", "setting-value", control?.value ?? "Unavailable on LG"),
      );
      if (this.host.pins().includes(r.key))
        b.append(node("i", "setting-pin", "•"));
      const help = () => {
        panel.replaceChildren(
          node("h2", "", r.title),
          node(
            "p",
            "",
            control?.help ||
              r.description.replace(/%\d+\$[sd]/g, "") ||
              r.title,
          ),
        );
        if (!supported)
          panel.append(
            node(
              "p",
              "settings-limitation",
              "This Android feature has not been implemented for LG webOS. It cannot be changed in this build.",
            ),
          );
        panel.append(
          node(
            "p",
            "muted",
            supported
              ? "OK  Change      Hold OK  Pin to Quick      Back  Settings"
              : "Back  Settings",
          ),
        );
      };
      b.addEventListener("focus", help);
      rows.append(b);
    }
    if (!rows.childElementCount)
      rows.append(node("p", "muted", "No settings match your search."));
    const back = button(
      "settings-back",
      "Back to Settings",
      () => {
        this.reset();
        this.render(main);
        this.host.focus("settings-group-quick");
      },
      "settings-back",
    );
    main.append(back);
  }
  private rows(group = this.group) {
    const pins = this.host.pins();
    const rows =
      group === "quick"
        ? pins
            .map((k) => catalogue.rows.find((r) => r.key === k))
            .filter((r): r is (typeof catalogue.rows)[number] => !!r)
        : catalogue.rows.filter((r) => r.group === group);
    return this.query
      ? catalogue.rows.filter((r) =>
          (r.title + " " + r.description + " " + r.group)
            .toLowerCase()
            .includes(this.query.toLowerCase()),
        )
      : rows;
  }
  long(el: HTMLElement) {
    const key = el.dataset.setting;
    if (!key) return false;
    this.host.menu(key);
    return true;
  }
  move(el: HTMLElement, dir: string) {
    if (!el.closest(".stage-settings")) return false;
    if (el.dataset.setting && (dir === "up" || dir === "down")) {
      const rows = [
          ...document.querySelectorAll<HTMLElement>(".stage-setting"),
        ],
        i = rows.indexOf(el),
        next = rows[i + (dir === "up" ? -1 : 1)];
      this.host.focus(
        next?.id ?? (dir === "up" ? "settings-search" : "settings-back"),
      );
      return true;
    }
    return false;
  }
}
