import { beginPhoneLogin, qrMarkup } from "./phone-login";
import { teamCrest } from "./team-crests";
import { heroAsset } from "./hero-art";
import { freeviewSource } from "./freeview";
import { GuideGrid } from "./guide-grid";
import { hasCurrentGuide, mergeGuide } from "./guide-policy";
import { channelRegion } from "./guide-match";
import { mergeFixtures } from "./sports";
import { SettingsScreen, SettingControl, SETTINGS_ROWS } from "./settings";
import { LoungeClient, request } from "./api";
import editorial from "./editorial.json";
import {
  TrendingCandidate,
  exactPicks,
  matchTrending,
  assembleTrending,
} from "./discovery";
import {
  Category,
  Credentials,
  Details,
  Kind,
  Media,
  Programme,
  SportEvent,
  duration,
  headlineEvents,
  key,
  nowProgramme,
  nzDate,
  nzDay,
  nzTime,
  selectEvents,
  sportLabel,
} from "./model";
import { LibraryState } from "./storage";
import { DeviceCache, completeCatalogue } from "./cache";
import { mapLimit } from "./tasks";
import { categoryGroups } from "./categories";
import { Direction, FocusController, VirtualCollection } from "./focus";
import { LgPlayback } from "./player";
import { button, icon, image, input, message, node, tag } from "./ui";

type Route =
  | "home"
  | "live"
  | "guide"
  | "movies"
  | "series"
  | "sports"
  | "saved"
  | "search"
  | "settings";
const app = document.getElementById("app")!,
  overlay = document.getElementById("overlay")!,
  hud = document.getElementById("player")!,
  video = document.getElementById("video") as HTMLVideoElement;
const state = new LibraryState(localStorage),
  focus = new FocusController(document.getElementById("viewport")!),
  player = new LgPlayback(video);
let trendingFeeds: {
  movies: TrendingCandidate[];
  series: TrendingCandidate[];
} = { movies: [], series: [] };
let client: LoungeClient,
  route: Route = "home",
  catalogues: Partial<Record<Kind, Media[]>> = {},
  categories: Partial<Record<Kind, Category[]>> = {},
  events: SportEvent[] = [],
  errors: Partial<Record<Kind | "sports", string>> = {},
  selected: Partial<Record<Kind, string>> = {},
  collections = new Map<string, VirtualCollection>(),
  routePositions = new Map<
    Route,
    { focus: string; scroll: Record<string, number> }
  >(),
  detailCache = new Map<string, Details>(),
  epgCache = new Map<string, { at: number; rows: Programme[] }>();
let view = 0,
  overlayVersion = 0,
  returnFocus = "",
  currentPlaying: Media | undefined,
  playingSports = false,
  preview = false,
  episodeQueue: Media[] = [],
  lastSave = 0,
  heroIndex = 0,
  heroTimer: ReturnType<typeof setInterval> | undefined,
  toastTimer: ReturnType<typeof setTimeout> | undefined,
  hudTimer: ReturnType<typeof setTimeout> | undefined,
  hoverTimer: ReturnType<typeof setTimeout> | undefined;
let tracksInitialised = false;
let accountGeneration = 0;
let focusEpoch = 0;
const deviceCache = new DeviceCache();
let activeAuth: Credentials | undefined;
let cacheAccount = "",
  starting = false,
  guideSummary:
    | {
        generation: string;
        at: number;
        channels: number;
        records: number;
        failedSources?: number;
        fallbackGeneration?: string;
        currentChannels?: number;
        totalChannels?: number;
        sourceStats?: {
          id: string;
          name: string;
          status: string;
          currentChannels: number;
          records: number;
        }[];
      }
    | undefined;
let startupRevision = 0,
  guideWorker: Worker | undefined;
const liveExpanded = new Set<string>();
let watchPanel: "channels" | "categories" | null = null,
  watchCategory = "",
  watchChannelCursor = "",
  watchChannelScroll = 0;
let traceSaveTimer: ReturnType<typeof setTimeout> | undefined;

const guideInFlight = new Map<string, Promise<Programme[]>>(),
  detailsInFlight = new Map<string, Promise<Details>>();
let browseItem: Media | undefined,
  sportTime = "live",
  sportFilter = "all",
  myTeams = false,
  searchQuery = "",
  savedTab = "favourites",
  guideStart = Math.floor(Date.now() / 1800000) * 1800000,
  guideOffset = 0;
const routes: { route: Route; label: string; icon: string }[] = [
  { route: "search", label: "Search", icon: "SEARCH" },
  { route: "home", label: "Home", icon: "HOME" },
  { route: "live", label: "Live TV", icon: "LIVE_TV" },
  { route: "sports", label: "Sport Lounge", icon: "SPORTS" },
  { route: "guide", label: "TV Guide", icon: "EPG" },
  { route: "movies", label: "Movies", icon: "MOVIES" },
  { route: "series", label: "Series", icon: "SERIES" },
  { route: "saved", label: "Saved", icon: "FAVORITE" },
  { route: "settings", label: "Settings", icon: "TILES" },
];
function toast(text: string) {
  const e = document.getElementById("toast")!;
  e.textContent = text;
  e.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => e.classList.remove("show"), 4000);
}
function safe(action: () => unknown) {
  try {
    const r = action();
    if (r instanceof Promise)
      r.catch((e) => toast(e.message ?? "Unable to complete that action."));
  } catch (e) {
    toast((e as Error).message);
  }
}
function action(
  id: string,
  label: string,
  fn: () => unknown,
  cls = "",
  glyph?: string,
) {
  return button(id, label, () => safe(fn), cls, glyph);
}
function resize() {
  const scale = Math.min(innerWidth / 1920, innerHeight / 1080);
  const el = document.getElementById("viewport")!;
  const nativeSize = innerWidth === 1920 && innerHeight === 1080;
  if (nativeSize || (currentPlaying && !preview)) {
    el.style.left = "0";
    el.style.top = "0";
    el.style.transform = "none";
  } else {
    el.style.left = "50%";
    el.style.top = "50%";
    el.style.transform = `translate(-50%,-50%) scale(${scale})`;
  }
}
window.addEventListener("resize", resize);
resize();
window.addEventListener("error", () =>
  toast("Something went wrong. Press Back and try again."),
);
window.addEventListener("unhandledrejection", (event) => {
  player.reportUnhandled(event.reason);
  persistPlaybackTrace();
  toast(
    player.failureSummary ||
      "Unable to complete that action. Please try again.",
  );
});
function savePosition() {
  if (
    !app.querySelector("main") ||
    !overlay.hidden ||
    !app.contains(document.activeElement) ||
    (currentPlaying && !preview)
  )
    return;
  const scroll: Record<string, number> = {};
  app
    .querySelectorAll<HTMLElement>("[data-scroll]")
    .forEach((e) => (scroll[e.id] = e.scrollTop));
  routePositions.set(route, {
    focus: (document.activeElement as HTMLElement)?.id ?? "",
    scroll,
  });
}
function destroyViews() {
  clearInterval(heroTimer);
  clearTimeout(hoverTimer);
  collections.forEach((c) => c.destroy());
  collections.clear();
  view++;
}
function closeOverlay() {
  collections.get("episode-list")?.destroy();
  collections.delete("episode-list");
  overlayVersion++;
  overlay.hidden = true;
  overlay.replaceChildren();
  focus.focus(returnFocus, false);
}
function openOverlay(title: string, cls = "modal") {
  collections.get("episode-list")?.destroy();
  collections.delete("episode-list");
  if (overlay.hidden || !overlay.contains(document.activeElement))
    returnFocus = (document.activeElement as HTMLElement)?.id ?? "";
  overlayVersion++;
  overlay.hidden = false;
  overlay.replaceChildren();
  const scrim = node("div", "scrim"),
    box = node("section", cls);
  box.dataset.focusScope = "";
  box.setAttribute("role", "dialog");
  box.setAttribute("aria-modal", "true");
  box.append(node("h2", "", title));
  overlay.append(scrim, box);
  return box;
}
function navEntry() {
  focus.focus("nav-" + route, false);
}
function renderShell() {
  const existing = document.getElementById("main");
  if (existing) {
    updateShell();
    return existing;
  }
  app.replaceChildren();
  app.append(node("div", "page-backdrop"));
  const nav = node("nav", "nav-rail");
  nav.setAttribute("aria-label", "Main navigation");
  nav.append(image("assets/nav-mark.png", "brand-mark", "Lounge TV"));
  for (const r of routes) {
    const b = action(
      "nav-" + r.route,
      r.label,
      () => {
        if (r.route === "movies" || r.route === "series")
          selected[r.route === "movies" ? "movie" : "series"] = undefined;
        navigate(r.route);
      },
      "nav-item" + (route === r.route ? " selected" : ""),
      r.icon,
    );
    b.setAttribute("aria-label", r.label);
    nav.append(b);
  }
  const profileButton = action(
    "nav-profile",
    "Profiles",
    profiles,
    "nav-profile",
    "PERSON",
  );
  profileButton.setAttribute("aria-label", "Profiles");
  nav.append(profileButton);
  const navButtons = Array.from(
    nav.querySelectorAll<HTMLElement>("[data-focus]"),
  );
  navButtons.forEach((b, i) => {
    b.dataset.up = navButtons[Math.max(0, i - 1)].id;
    b.dataset.down = navButtons[Math.min(navButtons.length - 1, i + 1)].id;
  });
  app.append(nav);
  const status = node("header", "status");
  const last = state.read<Media | null>("last-channel", null);
  if (last)
    status.append(
      action(
        "last-channel",
        "Last channel  " + last.name,
        () => play(last),
        "status-button",
        "LIVE_TV",
      ),
    );
  status.append(
    action(
      "active-profile",
      "Profile · " + state.profileName,
      profiles,
      "status-button",
      "LAYERS",
    ),
  );
  const clock = node("div", "clock");
  clock.id = "clock";
  status.append(clock);
  app.append(status);
  updateClock();
  const main = node("main");
  main.id = "main";
  app.append(main);
  return main;
}
function updateShell() {
  for (const r of routes) {
    const b = document.getElementById("nav-" + r.route);
    b?.classList.toggle("selected", r.route === route);
    if (b) b.setAttribute("aria-current", r.route === route ? "page" : "false");
  }
  const profile = document.querySelector("#active-profile span");
  if (profile) profile.textContent = "Profile · " + state.profileName;
}
function updateClock() {
  const el = document.getElementById("clock");
  if (el) {
    el.replaceChildren(
      node("strong", "", nzTime(Date.now())),
      node("span", "muted", nzDate(Date.now())),
    );
  }
}
setInterval(updateClock, 15000);
function navigate(next: Route, restore = false) {
  savePosition();
  if (currentPlaying) {
    if (!preview) {
      stopPlayback();
    } else if (next !== "live" && next !== "guide") stopPlayback();
  }
  destroyViews();
  if (next !== route && (next === "movies" || next === "series"))
    selected[next === "movies" ? "movie" : "series"] = undefined;
  if (next === "settings" && route !== "settings") settingScreen.reset();
  route = next;
  const main = renderShell();
  main.replaceChildren();
  main.className = "";
  switch (route) {
    case "home":
      renderHome(main);
      break;
    case "live":
      renderLive(main);
      break;
    case "guide":
      renderGuide(main);
      break;
    case "movies":
      renderBrowse(main, "movie");
      break;
    case "series":
      renderBrowse(main, "series");
      break;
    case "sports":
      renderSports(main);
      break;
    case "saved":
      renderSaved(main);
      break;
    case "search":
      renderSearch(main);
      break;
    case "settings":
      renderSettings(main);
      break;
  }
  const token = view,
    epoch = ++focusEpoch;
  const restoreFocus = () => {
    if (token !== view || epoch !== focusEpoch) return;
    if (route === "guide" && guidePendingFocus) {
      const target = guidePendingFocus;
      guidePendingFocus = undefined;
      guideGrid?.focus(target.index, target.time, !!target.channel);
      return;
    }
    const pos = routePositions.get(route);
    if (restore && pos) {
      for (const [id, top] of Object.entries(pos.scroll)) {
        const host = document.getElementById(id);
        if (host) {
          host.scrollTop = top;
          collections.get(id)?.render();
        }
      }
      if (focus.focus(pos.focus, false)) return;
    }
    const first =
      route === "live"
        ? main.querySelector<HTMLElement>("#live-category-tree [data-focus]")
        : route === "movies" || route === "series"
          ? main.querySelector<HTMLElement>("#browse-all")
          : route === "settings"
            ? main.querySelector<HTMLElement>("#settings-group-quick")
            : main.querySelector<HTMLElement>("[data-focus]");
    if (first) focus.focus(first.id, false);
    else navEntry();
  };
  restoreFocus();
}
function header(main: HTMLElement, title: string, subtitle?: string) {
  const h = node("div", "screen-heading");
  h.append(node("h1", "", title));
  if (subtitle) h.append(node("span", "muted", subtitle));
  main.append(h);
}
function unavailable(main: HTMLElement, kind: Kind) {
  const error = errors[kind];
  main.append(
    message(
      error ? "Unable to load content" : "Loading Lounge TV",
      error ?? "Your catalogue is loading.",
    ),
  );
  if (error)
    main.append(
      action(
        "reload-" + kind,
        "Try again",
        async () => {
          await loadKind(kind);
          navigate(route, true);
        },
        "retry",
        "REFRESH",
      ),
    );
}
async function loadKind(kind: Kind) {
  const generation = accountGeneration,
    source = client;
  delete errors[kind];
  try {
    const cats = await source.categories(kind);
    const rows = await source.catalogue(kind);
    if (generation !== accountGeneration) return;
    categories[kind] = cats;
    catalogues[kind] = rows;
  } catch (e) {
    if (generation === accountGeneration) errors[kind] = (e as Error).message;
  }
}
async function loadTrending() {
  const generation = accountGeneration;
  try {
    const [movie, series] = await mapLimit(["movie", "tv"], 2, (type) =>
      request(
        `https://metadata.loungetv.nz/3/trending/${type}/day?page=1&language=en-US`,
      ),
    );
    if (generation !== accountGeneration) return;
    trendingFeeds = {
      movies: movie.results ?? [],
      series: series.results ?? [],
    };
  } catch {
    /* Artwork never blocks catalogue readiness. */
  }
}
async function loadAll() {
  const generation = accountGeneration;
  await mapLimit(["live", "movie", "series"] as Kind[], 2, async (kind) => {
    await loadKind(kind);
    if (generation !== accountGeneration) return;
  });
}
let sportsLoading = false,
  sportsPaintTimer: ReturnType<typeof setTimeout> | undefined;
async function loadEvents() {
  if (sportsLoading) return;
  sportsLoading = true;
  const generation = accountGeneration,
    source = client;
  try {
    const saved = await deviceCache.get<{ at: number; rows: SportEvent[] }>(
      cacheAccount + ":sports",
    );
    if (saved && Date.now() - saved.at < 5 * 60000 && !events.length)
      events = saved.rows;
    const rows = await source.events((partial) => {
      if (generation !== accountGeneration) return;
      if (partial.length) events = mergeFixtures([...events, ...partial]);
      if (!starting && route === "sports" && overlay.hidden) {
        clearTimeout(sportsPaintTimer);
        sportsPaintTimer = setTimeout(() => {
          if (route === "sports" && overlay.hidden) navigate("sports", true);
        }, 300);
      }
    });
    if (generation !== accountGeneration) return;
    events = rows;
    void deviceCache.put(cacheAccount + ":sports", { at: Date.now(), rows });
    delete errors.sports;
    if (!starting && route === "sports" && overlay.hidden)
      navigate("sports", true);
  } catch (e) {
    if (generation === accountGeneration) errors.sports = (e as Error).message;
  } finally {
    sportsLoading = false;
  }
}
function showStartup() {
  starting = true;
  destroyViews();
  overlay.hidden = true;
  hud.hidden = true;
  app.replaceChildren();
  const screen = node("section", "startup");
  screen.id = "startup";
  screen.dataset.focusScope = "";
  screen.append(
    image("assets/nav-mark.png", "startup-logo", "Lounge TV"),
    node("h1", "", "Getting your lounge ready"),
    node("p", "muted", "Loading your playlist and programme guide."),
  );
  const stages = node("ol", "startup-stages");
  for (const [id, label] of [
    ["account", "Checking your membership"],
    ["catalogue", "Preparing your playlist"],
    ["guide", "Preparing your TV guide"],
    ["ready", "Opening your lounge"],
  ]) {
    const row = node("li", "");
    row.id = "stage-" + id;
    row.append(node("i", "stage-light"), node("span", "", label));
    stages.append(row);
  }
  const status = node("p", "startup-status", "Connecting…");
  status.id = "startup-status";
  status.setAttribute("role", "status");
  status.setAttribute("aria-live", "polite");
  const counts = node("p", "startup-counts");
  counts.id = "startup-counts";
  screen.append(stages, status, counts);
  app.append(screen);
}
function startupStage(id: string, text: string) {
  const rows = ["account", "catalogue", "guide", "ready"],
    at = rows.indexOf(id);
  rows.forEach((key, i) => {
    const el = document.getElementById("stage-" + key);
    el?.classList.toggle("done", i < at);
    el?.classList.toggle("active", i === at);
  });
  const status = document.getElementById("startup-status");
  if (status) status.textContent = text;
}
async function loadFullGuide(onProgress: (text: string) => void) {
  if (guideWorker) throw Error("Guide refresh is already running.");
  const generation = accountGeneration,
    account = cacheAccount,
    now = Date.now(),
    version = String(now);
  const ids = [
    ...new Set((catalogues.live ?? []).map((m) => m.epgId).filter(Boolean)),
  ] as string[];
  if (!ids.length) throw Error("No guide channel identifiers were supplied.");
  await new Promise<void>((resolve, reject) => {
    const worker = new Worker("epg.worker.js");
    guideWorker = worker;
    let writes = Promise.resolve(),
      done = false;
    const timer = setTimeout(
      () => finish(Error("Programme guide timed out")),
      125000,
    );
    function finish(error?: Error) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      worker.terminate();
      if (guideWorker === worker) guideWorker = undefined;
      error ? reject(error) : resolve();
    }
    worker.onerror = () => finish(Error("Programme guide unavailable"));
    worker.onmessage = ({ data }) => {
      if (generation !== accountGeneration) {
        finish(Error("Session changed"));
        return;
      }
      if (data.type === "progress")
        onProgress(
          `${Number(data.records).toLocaleString()} programmes · ${Number(data.channels).toLocaleString()} guide channels`,
        );
      if (data.type === "batch")
        writes = writes
          .then(() =>
            deviceCache.putGuideBatch(account, version, data.rows, now),
          )
          .catch(() => {
            finish(Error("TV guide could not be cached."));
          });
      if (data.type === "error") finish(Error(data.message));
      if (data.type === "complete")
        void writes
          .then(async () => {
            if (done || generation !== accountGeneration) return;
            const previous = guideSummary;
            guideSummary = {
              generation: version,
              fallbackGeneration:
                data.failed && previous
                  ? previous.fallbackGeneration || previous.generation
                  : undefined,
              at: now,
              channels: data.channels,
              records: data.records,
              failedSources: data.failed,
              currentChannels: data.currentChannels,
              totalChannels: data.totalChannels,
              sourceStats: data.sourceStats,
            };
            await deviceCache.put(account + ":guide-summary", guideSummary);
            state.write("guide-client-revision", 4);
            void deviceCache.pruneGuides(
              account,
              version,
              guideSummary.fallbackGeneration,
            );
            finish();
          })
          .catch(() => finish(Error("TV guide could not be cached.")));
    };
    worker.postMessage({
      url: client.guideUrl(),
      urls: guideSources()
        .filter((s) => s.enabled)
        .map((s) => s.url),
      ids,
      sources: guideSources().filter((s) => s.enabled),
      channels: (catalogues.live ?? []).map((m) => ({
        ...m,
        cacheKey: "stream:" + m.id,
        region: channelRegion(
          m,
          categories.live?.find((c) => c.id === m.category)?.name,
        ),
      })),
      overrides: state.read("guide-matches", {}),
      now,
      days: state.settings().guideDays,
    });
  });
}
async function primeGuide() {
  const live = catalogues.live ?? [],
    firstCategory = selected.live ?? categories.live?.[0]?.id;
  const important = [
    state.read<Media | null>("last-channel", null),
    ...state.favourites().filter((m) => m.kind === "live"),
    ...live.filter((m) => m.category === firstCategory),
  ].filter(Boolean) as Media[];
  const ids = [...new Set(important.map((m) => m.id))].slice(0, 12);
  let completed = 0,
    failed = 0;
  await mapLimit(ids, 3, async (id) => {
    try {
      await guide(id);
    } catch {
      failed++;
    }
    completed++;
    startupStage("guide", `Preparing channels ${completed} / ${ids.length}`);
  });
  return { failed, total: ids.length };
}
let cancelPhoneLogin: (() => void) | undefined;
async function startSession(auth: Credentials, force = false) {
  cancelPhoneLogin?.();
  cancelPhoneLogin = undefined;
  const revision = ++startupRevision;
  showStartup();
  const generation = ++accountGeneration;
  guideInFlight.clear();
  detailsInFlight.clear();
  epgCache.clear();
  guideFallbackAt.clear();
  detailCache.clear();
  activeAuth = auth;
  client = new LoungeClient(auth);
  startupStage("account", "Checking your Lounge TV membership…");
  try {
    await client.login();
    if (revision !== startupRevision) return;
    state.scope(auth.username);
    applyPreferences();
    cacheAccount = auth.username;
    state.settings().rememberLogin
      ? state.remember(auth)
      : state.remember(null);
    if (state.settings().rememberLogin) state.savePlaylist(auth);
    guideSummary = await deviceCache.get(cacheAccount + ":guide-summary");
    const optional = Promise.allSettled([loadEvents(), loadTrending()]);
    startupStage("catalogue", "Checking the playlist saved on this TV…");
    const saved = await deviceCache.catalogue(cacheAccount);
    const useSaved =
      !force && completeCatalogue(saved) && Date.now() - saved.at < 6 * 3600000;
    if (useSaved) {
      categories = saved.categories;
      catalogues = saved.catalogues;
      startupStage("catalogue", "Playlist restored from this TV.");
    } else {
      let finished = 0;
      const nextCats: Partial<Record<Kind, Category[]>> = {},
        nextRows: Partial<Record<Kind, Media[]>> = {};
      await mapLimit(["live", "movie", "series"] as Kind[], 2, async (kind) => {
        const cats = await client.categories(kind),
          rows = await client.catalogue(kind);
        nextCats[kind] = cats;
        nextRows[kind] = rows;
        finished++;
        startupStage(
          "catalogue",
          `${finished} / 3 libraries ready · ${kind === "live" ? "Live TV" : kind === "movie" ? "Movies" : "Series"}: ${rows.length.toLocaleString()}`,
        );
      });
      if (generation !== accountGeneration) return;
      categories = nextCats;
      catalogues = nextRows;
      await deviceCache.saveCatalogue(cacheAccount, {
        at: Date.now(),
        categories,
        catalogues,
      });
    }
    startupStage("catalogue", "Preparing Movies and Series for browsing…");
    await warmLibraries();
    const counts = document.getElementById("startup-counts");
    if (counts)
      counts.textContent = `${(catalogues.live?.length ?? 0).toLocaleString()} channels · ${(catalogues.movie?.length ?? 0).toLocaleString()} movies · ${(catalogues.series?.length ?? 0).toLocaleString()} series`;
    if (!selected.live || !categories.live?.some((c) => c.id === selected.live))
      selected.live = categories.live?.[0]?.id;
    startupStage("guide", "Checking programme guide…");
    let guideWarning = "";
    if (
      force ||
      !guideSummary ||
      state.read("guide-client-revision", 0) < 4 ||
      Date.now() - guideSummary.at >
        (guideSummary.failedSources
          ? 15 * 60000
          : state.settings().guideRefreshHours * 3600000)
    ) {
      try {
        await loadFullGuide((text) => startupStage("guide", text));
      } catch {
        guideWarning =
          "The full guide is unavailable. Loaded channel guides where available.";
      }
    }
    const primed = await primeGuide();
    if (primed.failed && primed.failed === primed.total)
      guideWarning =
        "Programme guide unavailable. You can retry it from Settings.";
    await Promise.race([optional, new Promise((r) => setTimeout(r, 1000))]);
    if (revision !== startupRevision || generation !== accountGeneration)
      return;
    startupStage("ready", "Your lounge is ready.");
    starting = false;
    app.replaceChildren();
    routePositions.clear();
    const initial = state.settings().startup as Route;
    navigate(
      ["home", "live", "movies", "series", "sports"].includes(initial)
        ? initial
        : "home",
    );
    if (guideWarning) toast(guideWarning);
  } catch (error) {
    if (revision !== startupRevision) return;
    starting = false;
    const status = document.getElementById("startup-status");
    if (status) {
      status.textContent = (error as Error).message;
      status.classList.add("error");
    }
    const screen = document.getElementById("startup");
    screen?.append(
      action(
        "startup-retry",
        "Try again",
        () => startSession(auth, force),
        "primary",
        "REFRESH",
      ),
      action(
        "startup-signout",
        "Change login",
        () => {
          accountGeneration++;
          state.remember(null);
          login();
        },
        "",
        "PERSON",
      ),
    );
    focus.focus("startup-retry");
  }
}
function login(error = "") {
  cancelPhoneLogin?.();
  cancelPhoneLogin = undefined;
  destroyViews();
  routePositions.clear();
  app.replaceChildren();
  const screen = node("section", "login");
  screen.dataset.focusScope = "";
  screen.append(
    image("assets/wordmark.png", "login-logo", "Lounge TV"),
    node("h1", "", "Welcome to your lounge"),
    node("p", "muted", "Sign in with your Lounge TV username and password."),
  );
  const form = node("form");
  const user = input("login-user", "Username"),
    pass = input("login-password", "Password", true);
  const saved = state.credentials();
  if (saved) {
    user.value = saved.username;
    pass.value = saved.password;
  }
  user.dataset.down = pass.id;
  pass.dataset.up = user.id;
  pass.dataset.down = "login-submit";
  const err = node("p", "error", error);
  const submit = button("login-submit", "Sign in", () => {}, "primary", "PLAY");
  submit.type = "submit";
  submit.dataset.up = pass.id;
  form.append(user, pass, err, submit);
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (submit.disabled) return;
    const auth = { username: user.value.trim(), password: pass.value };
    if (!auth.username || !auth.password) {
      err.textContent = "Enter your Lounge TV username and password.";
      return;
    }
    submit.disabled = true;
    submit.querySelector("span")!.textContent = "Signing in…";
    try {
      await startSession(auth);
    } catch (e) {
      err.textContent = (e as Error).message;
      submit.disabled = false;
      submit.querySelector("span")!.textContent = "Sign in";
      focus.focus("login-submit");
    }
  });
  const choices = node("div", "login-choices"),
    remote = node("section", "login-remote");
  remote.append(node("h2", "", "Use your remote"), form);
  const phone = node("section", "login-phone");
  phone.append(
    node("h2", "", "Sign in with your phone"),
    node(
      "p",
      "muted",
      "Scan the QR code on the same Wi-Fi. Enter your details on your phone.",
    ),
  );
  const qr = node("div", "login-qr");
  qr.id = "login-qr";
  const status = node(
    "p",
    "muted",
    "Choose phone sign-in to show your QR code.",
  );
  status.id = "phone-status";
  const refresh = action(
    "login-phone",
    "Show QR code",
    () => {
      cancelPhoneLogin?.();
      qr.replaceChildren();
      status.textContent = "Preparing your QR code…";
      cancelPhoneLogin = beginPhoneLogin({
        ready: (url) => {
          if (!screen.isConnected) return;
          qr.innerHTML = qrMarkup(url);
          qr.setAttribute("role", "img");
          qr.setAttribute("aria-label", "Scan to sign in on this LG TV");
          status.textContent =
            "Ready to scan. This code expires in 10 minutes.";
          refresh.querySelector("span")!.textContent = "Refresh QR code";
        },
        credentials: (auth) => {
          if (screen.isConnected) void startSession(auth);
        },
        error: (message) => {
          if (screen.isConnected) {
            status.textContent = message;
            qr.replaceChildren();
          }
        },
      });
    },
    "primary",
    "PHONE",
  );
  phone.append(qr, status, refresh);
  choices.append(phone, remote);
  screen.append(choices, node("p", "login-footer", "LOUNGE TV · LG webOS"));
  app.append(screen);
  focus.focus("login-phone");
  if ((window as any).PalmServiceBridge) refresh.click();
}
function profiles() {
  const box = openOverlay("Who’s watching?");
  for (const p of state.profiles())
    box.append(
      action(
        "profile-" + p.id,
        p.name,
        () => {
          state.switchProfile(p.id);
          closeOverlay();
          routePositions.clear();
          navigate("home");
        },
        "profile-choice",
        "PERSON",
      ),
    );
  const name = input("profile-name", "New profile name");
  box.append(
    name,
    action(
      "profile-add",
      "Add profile",
      () => {
        state.addProfile(name.value);
        closeOverlay();
        navigate("home");
      },
      "",
      "PLUS",
    ),
    action("profile-close", "Close", closeOverlay),
  );
  focus.focus("profile-" + state.profile);
}
function renderHome(main: HTMLElement) {
  main.className = "home";
  const scroller = node("div", "home-scroll");
  scroller.id = "home-scroll";
  scroller.dataset.scroll = "";
  main.append(scroller);
  const hero = node("section", "major-hero");
  hero.id = "major-hero";
  scroller.append(hero);
  renderHero(hero);
  heroTimer = setInterval(() => {
    if (
      !overlay.hidden ||
      (currentPlaying && !preview) ||
      document.activeElement?.closest("#major-hero")
    )
      return;
    const pool = headlineEvents(events);
    if (pool.length > 1) {
      heroIndex = (heroIndex + 1) % pool.length;
      renderHero(hero);
    }
  }, 10000);
  const continuing = state.continuing();
  if (continuing.length)
    rail(
      scroller,
      "Keep watching",
      continuing.map((x) => x.media),
      "continue",
      true,
    );
  const movies = catalogues.movie ?? [],
    series = catalogues.series ?? [];
  const trending = assembleTrending(
    matchTrending(movies, trendingFeeds.movies),
    matchTrending(series, trendingFeeds.series),
  );
  if (trending.length) rail(scroller, "Now trending", trending, "trending");
  const featured = exactPicks(movies, editorial.lounge_popular_movies),
    popular = exactPicks(series, editorial.lounge_popular_series);
  if (featured.length) rail(scroller, "Featured movies", featured, "featured");
  if (popular.length) rail(scroller, "Popular series", popular, "popular");
  if (events.length) {
    const h = node("section", "home-rail");
    h.append(node("h2", "", "Live sports"));
    const row = node("div", "event-rail");
    events
      .filter((e) => e.state !== "finished")
      .slice(0, 12)
      .forEach((e) => row.append(eventCard(e)));
    h.append(row);
    scroller.append(h);
  }
  if (!movies.length && !series.length)
    scroller.append(
      message(
        errors.movie ? "Content unavailable" : "Loading your lounge",
        errors.movie ?? "Your movies, series and channels will appear here.",
      ),
    );
}
function renderHero(hero: HTMLElement) {
  const old = (document.activeElement as HTMLElement)?.id;
  hero.replaceChildren();
  const pool = headlineEvents(events),
    event = pool[heroIndex % Math.max(1, pool.length)];
  if (!event) {
    hero.append(node("div", "hero-wash"));
    const copy = node("div", "hero-copy");
    copy.append(
      node("div", "eyebrow", "BIG EVENTS. LIVE ON LOUNGE."),
      node("h2", "", "Your next big event"),
      node(
        "p",
        "muted",
        errors.sports ?? "Loading the current Sport Lounge schedule.",
      ),
      action(
        "hero-view",
        "Open Sport Lounge",
        () => navigate("sports"),
        "",
        "SPORTS",
      ),
    );
    hero.append(copy);
    return;
  }
  const fallback = heroAsset(event.sport),
    art = event.hero_art_url || fallback;
  if (art) {
    const cover = image(art, "hero-image");
    cover.loading = "eager";
    let usedFallback = art === fallback;
    cover.addEventListener("error", () => {
      if (usedFallback || !fallback) return;
      usedFallback = true;
      cover.hidden = false;
      cover.src = fallback;
    });
    hero.append(cover);
  }
  hero.append(node("div", "hero-wash"));
  const teamSports = new Set([
    "basketball",
    "hockey",
    "ice-hockey",
    "rugby",
    "rugby-union",
    "rugby-league",
    "soccer",
    "football",
    "baseball",
    "cricket",
    "nfl",
    "nba",
    "nhl",
  ]);
  if (
    teamSports.has(event.sport.toLowerCase()) &&
    event.competitors.length === 2
  ) {
    const teams = node("div", "hero-teams");
    teams.setAttribute("aria-label", "Teams");
    event.competitors.forEach((team, index) => {
      if (index) teams.append(node("span", "versus", "vs"));
      const club = node("div", "hero-team");
      const crest = teamCrest(event, team);
      if (crest) {
        const logo = image(crest, "team-crest", team.name + " crest");
        logo.loading = "eager";
        club.append(logo);
      }
      club.append(node("b", "", team.shortName || team.name));
      teams.append(club);
    });
    hero.append(teams);
  }
  const copy = node("div", "hero-copy");
  copy.append(node("div", "eyebrow", "BIG EVENTS. LIVE ON LOUNGE."));
  const tags = node("div", "tags");
  tags.append(
    tag(event.state === "live" ? "LIVE NOW" : "UPCOMING EVENT"),
    tag(event.leagueName),
  );
  copy.append(
    tags,
    node("h2", "", event.editorial_title || event.title),
    node(
      "h3",
      "",
      `${nzDate(event.startUtcMs)} · ${nzTime(event.startUtcMs)} · NZ time`,
    ),
  );
  if (event.venue) copy.append(node("p", "hero-venue", event.venue));
  if (event.synopsis || event.subtitle)
    copy.append(node("p", "hero-synopsis", event.synopsis || event.subtitle));
  const actions = node("div", "actions");
  actions.append(
    action(
      "hero-view",
      event.state === "live" ? "Find channels" : "View event",
      () => openEvent(event),
      "",
      "PLAY",
    ),
  );
  if (event.state === "scheduled")
    actions.append(
      action(
        "hero-remind",
        state.reminders().some((x) => x.id === event.id)
          ? "Reminder set"
          : "Remind me",
        () => {
          state.toggleReminder(event);
          renderHero(hero);
        },
        "",
        "BELL",
      ),
    );
  copy.append(actions);
  const pager = node("div", "hero-pager");
  pool.forEach((e, i) => {
    const b = action(
      "hero-page-" + i,
      `Event ${i + 1}`,
      () => {
        heroIndex = i;
        renderHero(hero);
      },
      i === heroIndex % pool.length ? "on" : "",
    );
    b.setAttribute("aria-label", e.title);
    b.dataset.heroIndex = String(i);
    b.addEventListener("focus", () => {
      if (heroIndex % pool.length === i) return;
      heroIndex = i;
      renderHero(hero);
    });
    pager.append(b);
  });
  copy.append(
    pager,
    node(
      "p",
      "hero-disclaimer",
      "Available streams are checked when the event starts.",
    ),
  );
  hero.append(copy);
  if (old?.startsWith("hero-")) focus.focus(old, false);
}
function poster(item: Media, id: string, still = false) {
  const b = action(
    id,
    "",
    () => (item.kind === "live" ? play(item) : openDetails(item)),
    "poster" + (still ? " still" : ""),
  );
  const art = node("div", "poster-art");
  art.append(image(item.image, "poster-image", item.name));
  if (item.rating > 0 && state.settings().showRatings)
    art.append(node("span", "rating", `★ ${item.rating.toFixed(1)}`));
  if (state.saved(item)) art.append(node("span", "saved-badge", "♥"));
  const progress = state.progressFor(item);
  if (progress && progress.duration > 0) {
    const line = node("div", "progress");
    const fill = node("i");
    fill.style.width = `${Math.min(100, (100 * progress.position) / progress.duration)}%`;
    line.append(fill);
    art.append(line);
  }
  b.append(art, node("span", "poster-title", item.name));
  b.dataset.media = key(item);
  return b;
}
function rail(
  host: HTMLElement,
  title: string,
  items: Media[],
  id: string,
  still = false,
) {
  const section = node("section", "home-rail");
  section.append(node("h2", "", title));
  const row = node("div", "poster-rail");
  row.id = "rail-" + id;
  items.forEach((m, i) => row.append(poster(m, id + "-" + i, still)));
  section.append(row);
  host.append(section);
}
function liveCategoryPanel(
  host: HTMLElement,
  prefix: string,
  onSelect: (id: string) => void,
  selectedId = selected.live,
) {
  const rows = (categories.live ?? []).filter(
      (c) =>
        !state.read<string[]>("hidden-categories", []).includes("live:" + c.id),
    ),
    groups = categoryGroups(rows),
    selectedCategory = rows.find((c) => c.id === selectedId);
  const counts = new Map<string, number>();
  for (const m of catalogues.live ?? [])
    counts.set(m.category, (counts.get(m.category) ?? 0) + 1);
  if (selectedCategory && !liveExpanded.size)
    liveExpanded.add(selectedCategory.section);
  const draw = () => {
    const active = document.activeElement?.id;
    host.replaceChildren();
    host.append(
      action(
        prefix + "all",
        "All channels",
        () => onSelect(""),
        "category-row" + (selectedId === "" ? " chosen" : ""),
        "LIVE_TV",
      ),
    );
    groups.forEach((group, i) => {
      const id = prefix + "group-" + i,
        expanded = liveExpanded.has(group.name);
      const heading = action(
        id,
        group.name,
        () => {
          if (liveExpanded.has(group.name)) liveExpanded.delete(group.name);
          else {
            liveExpanded.clear();
            liveExpanded.add(group.name);
          }
          draw();
          focus.focus(id, false);
        },
        "category-heading",
        expanded ? "CHEVRON_DOWN" : "CHEVRON",
      );
      heading.setAttribute("aria-expanded", String(expanded));
      host.append(heading);
      if (expanded)
        for (const cat of group.items) {
          const count = counts.get(cat.id) ?? 0;
          const b = action(
            prefix + "category-" + cat.id,
            cat.label,
            () => onSelect(cat.id),
            "category-row subcategory" +
              (selectedId === cat.id ? " chosen" : ""),
          );
          b.setAttribute(
            "aria-current",
            selectedId === cat.id ? "true" : "false",
          );
          b.append(node("small", "category-count", String(count)));
          b.dataset.category = cat.id;
          host.append(b);
        }
    });
    if (active && host.contains(document.getElementById(active)))
      focus.focus(active, false);
  };
  draw();
}
function categoryPicker(kind: Kind, onSelect?: () => void) {
  const box = openOverlay(
    kind === "live"
      ? "Live TV categories"
      : kind === "movie"
        ? "Movie categories"
        : "Series categories",
    "category-sheet",
  );
  const choose = (id: string) => {
    if (id) selected[kind] = id;
    else if (kind === "live") selected.live = "";
    else delete selected[kind];
    closeOverlay();
    onSelect ? onSelect() : navigate(route);
  };
  if (kind === "live") {
    const tree = node("div", "category-tree");
    tree.id = "category-tree";
    box.append(tree);
    liveCategoryPanel(tree, "picker-", choose);
  } else {
    box.append(
      action(
        "category-all",
        "All " + (kind === "movie" ? "movies" : "series"),
        () => choose(""),
        "category-row",
      ),
    );
    for (const cat of (categories[kind] ?? []).filter(
      (c) =>
        !state
          .read<string[]>("hidden-categories", [])
          .includes(kind + ":" + c.id),
    ))
      box.append(
        action(
          "category-" + cat.id,
          cat.label,
          () => choose(cat.id),
          "category-row" + (selected[kind] === cat.id ? " chosen" : ""),
        ),
      );
  }
  box.append(action("categories-close", "Close", closeOverlay, "category-row"));
  const first = box.querySelector<HTMLElement>("[data-focus]");
  if (first) focus.focus(first.id);
}
function filtered(kind: Kind, query = "") {
  const hidden = state.read<string[]>("hidden-categories", []);
  const rows = (catalogues[kind] ?? []).filter(
    (m) => !hidden.includes(kind + ":" + m.category),
  );
  return rows.filter(
    (m) =>
      (!selected[kind] || m.category === selected[kind]) &&
      (!query || m.name.toLowerCase().includes(query.toLowerCase())),
  );
}
async function cachedGuide(id: string): Promise<Programme[] | undefined> {
  const memory = epgCache.get(id);
  if (memory && Date.now() - memory.at < 120000) return memory.rows;
  const generation = accountGeneration,
    account = cacheAccount,
    m = catalogues.live?.find((m) => m.id === id);
  let rows: Programme[] | undefined;
  if (m?.epgId && guideSummary) {
    const full = await deviceCache.get<{ at: number; rows: Programme[] }>(
      `${account}:xmltv:${guideSummary.generation}:stream:${m.id}`,
    );
    if (full && full.rows.some((p) => p.end > Date.now())) rows = full.rows;
    if (guideSummary.fallbackGeneration && !hasCurrentGuide(rows)) {
      const retained = await deviceCache.get<{ at: number; rows: Programme[] }>(
        `${account}:xmltv:${guideSummary.fallbackGeneration}:stream:${m.id}`,
      );
      if (retained?.rows.some((p) => p.end > Date.now()))
        rows = mergeGuide(rows ?? [], retained.rows);
    }
  }
  if (!rows && state.read("guide-canonical-enabled", true)) {
    const saved = await deviceCache.guide(account, id);
    if (saved && Date.now() - saved.at < 15 * 60000) rows = saved.rows;
  }
  if (rows?.length && generation === accountGeneration) {
    epgCache.set(id, { at: Date.now(), rows });
    if (epgCache.size > 160) epgCache.delete(epgCache.keys().next().value!);
  }
  return rows;
}
const guideFallbackAt = new Map<string, number>();
const visibleGuideQueue: {
  id: string;
  view: number;
  resolve: (rows: Programme[]) => void;
}[] = [];
let visibleGuideActive = 0;
const visibleGuidePending = new Map<string, Promise<Programme[]>>();
function queuedGuide(id: string): Promise<Programme[]> {
  const key = accountGeneration + ":" + view + ":" + id,
    pending = visibleGuidePending.get(key);
  if (pending) {
    const at = visibleGuideQueue.findIndex(
      (j) => j.id === id && j.view === view,
    );
    if (at > 0) visibleGuideQueue.unshift(...visibleGuideQueue.splice(at, 1));
    queueMicrotask(drainGuides);
    return pending;
  }
  const work = new Promise<Programme[]>((resolve) => {
    visibleGuideQueue.push({ id, view, resolve });
    queueMicrotask(drainGuides);
  });
  visibleGuidePending.set(key, work);
  void work.finally(() => visibleGuidePending.delete(key));
  return work;
}

function drainGuides() {
  while (visibleGuideActive < 2 && visibleGuideQueue.length) {
    const job = visibleGuideQueue.shift()!;
    const visible =
      document.getElementById("channel-" + job.id) ||
      [...document.querySelectorAll<HTMLElement>(".guide-row")].some(
        (row) => row.dataset.guideChannel === job.id,
      );
    if (job.view !== view || !visible) {
      job.resolve([]);
      continue;
    }
    visibleGuideActive++;
    void guide(job.id)
      .then(job.resolve, () => job.resolve([]))
      .finally(() => {
        visibleGuideActive--;
        drainGuides();
      });
  }
}
async function guideRaw(id: string): Promise<Programme[]> {
  if (!guideSources().some((s) => s.enabled)) return [];
  const pending = guideInFlight.get(id);
  if (pending) return pending;
  const generation = accountGeneration,
    account = cacheAccount,
    source = client;
  const work = (async () => {
    const cached = await cachedGuide(id);
    if (hasCurrentGuide(cached)) return cached!;
    if (Date.now() - (guideFallbackAt.get(id) ?? 0) < 60000)
      return cached ?? [];
    const m = catalogues.live?.find((m) => m.id === id),
      override = state.read<Record<string, string>>("guide-matches", {})[id];
    let rows: Programme[] = [];
    try {
      if (state.read("guide-canonical-enabled", true))
        rows = await source.guide(id, override || m?.epgId, !override);
    } catch {
      if (!cached?.length) throw Error("Programme guide unavailable");
    } finally {
      if (generation === accountGeneration) guideFallbackAt.set(id, Date.now());
    }
    rows = mergeGuide(rows, cached ?? []);
    await deviceCache.saveGuide(account, id, { at: Date.now(), rows });
    return rows;
  })()
    .then((rows) => {
      if (generation === accountGeneration) {
        epgCache.set(id, { at: Date.now(), rows });
        if (epgCache.size > 160) epgCache.delete(epgCache.keys().next().value!);
      }
      return rows;
    })
    .finally(() => guideInFlight.delete(id));
  guideInFlight.set(id, work);
  return work;
}
async function guide(id: string) {
  const rows = await guideRaw(id),
    shift = state.settings().epgOffset * 60000;
  return shift
    ? rows.map((p) => ({ ...p, start: p.start + shift, end: p.end + shift }))
    : rows;
}
interface GuideSource {
  id: string;
  name: string;
  url: string;
  enabled: boolean;
  builtin?: boolean;
  regions?: string[];
}
let guideRegionRows: Media[] | undefined,
  guideRegionSet = new Set<string | undefined>();
function guideSources(): GuideSource[] {
  const disabled = state.read<string[]>("guide-builtins-disabled", []);
  if (guideRegionRows !== catalogues.live) {
    guideRegionRows = catalogues.live;
    guideRegionSet = new Set(
      (catalogues.live ?? []).map((m) =>
        channelRegion(
          m,
          categories.live?.find((c) => c.id === m.category)?.name,
        ),
      ),
    );
  }
  const regions = guideRegionSet;
  return [
    {
      id: "lounge",
      name: "Lounge TV programme guide",
      url: client.guideUrl(),
      enabled: state.read("guide-canonical-enabled", true),
      builtin: true,
    },
    ...(regions.has("nz")
      ? [
          {
            id: "nz",
            name: "New Zealand guide fallback",
            url: "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/nz/epg.xml.gz",
            enabled: !disabled.includes("nz"),
            builtin: true,
            regions: ["nz"],
          },
        ]
      : []),
    ...(regions.has("au")
      ? [
          {
            id: "au",
            name: "Australia guide fallback (Sydney)",
            url: "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/au/Sydney/epg.xml.gz",
            enabled: !disabled.includes("au"),
            builtin: true,
            regions: ["au"],
          },
        ]
      : []),
    ...state.read<GuideSource[]>("guide-sources", []),
  ];
}

function progressBar(p: Programme | undefined) {
  const line = node("div", "progress");
  const fill = node("i");
  fill.style.width = p
    ? `${Math.max(0, Math.min(100, (100 * (Date.now() - p.start)) / (p.end - p.start)))}%`
    : "0%";
  line.append(fill);
  return line;
}
function renderLive(main: HTMLElement) {
  main.className = "live";
  if (!catalogues.live) {
    header(main, "Live TV");
    unavailable(main, "live");
    return;
  }
  if (selected.live === undefined) selected.live = categories.live?.[0]?.id;
  const panel = node("section", "live-category-panel");
  panel.setAttribute("aria-label", "Live TV categories");
  panel.append(node("h2", "", "Categories"));
  const tree = node("div", "live-category-tree");
  tree.id = "live-category-tree";
  tree.dataset.scroll = "";
  panel.append(tree);
  main.append(panel);
  const heading = node("div", "live-heading"),
    label = node("span", "live-crumb"),
    count = node("span", "muted");
  heading.append(node("h1", "", "Live TV"), label, count);
  main.append(heading);
  const toolbar = node("div", "live-toolbar"),
    search = input("live-search", "Search channels");
  toolbar.append(
    search,
    action("live-guide", "Guide", () => navigate("guide"), "tool", "GRID"),
  );
  main.append(toolbar);
  const host = node("div", "channel-list");
  host.id = "channels";
  host.dataset.scroll = "";
  main.append(host);
  const pane = node("section", "live-pane");
  pane.id = "live-pane";
  main.append(pane);
  const footer = node("div", "key-hints");
  footer.textContent =
    "OK  Watch       ◀  Categories       ▶  Schedule       Hold OK  Options";
  main.append(footer);
  const token = view;
  let localItems: Media[] = [];
  const choose = (id: string) => {
    selected.live = id;
    search.value = "";
    routePositions.delete("live");
    renderTree();
    list();
    collections.get("channels")?.focus(0);
  };
  const renderTree = () => liveCategoryPanel(tree, "live-", choose);
  function list() {
    collections.get(host.id)?.destroy();
    host.replaceChildren();
    localItems = filtered("live", search.value);
    label.textContent =
      categories.live?.find((c) => c.id === selected.live)?.label ??
      "All channels";
    count.textContent = "· " + localItems.length + " channels";
    const collection = new VirtualCollection(
      host,
      localItems.length,
      1,
      92,
      (i) => {
        const m = localItems[i],
          b = action("channel-" + m.id, "", () => play(m), "channel-row");
        b.dataset.media = key(m);
        b.append(
          node(
            "span",
            "channel-number",
            state.settings().channelNumbers ? String(i + 1) : "",
          ),
          image(m.image, "channel-logo"),
          node("div", "channel-info"),
        );
        const text = b.querySelector(".channel-info")!;
        text.append(
          node("b", "", m.name),
          node("span", "muted", "Programme information loading"),
          progressBar(undefined),
        );
        b.addEventListener("focus", () => {
          browseItem = m;
          clearTimeout(hoverTimer);
          hoverTimer = setTimeout(
            () => void updateLivePane(pane, m, token),
            state.settings().livePreview ? 800 : 160,
          );
        });
        const cached = epgCache.get(m.id)?.rows;
        if (cached) setChannelGuide(b, cached);
        // EPG lookup is cache-first and deduplicated; rows update without rebuilding the list.
        void queuedGuide(m.id)
          .then((rows) => {
            if (token === view && b.isConnected) setChannelGuide(b, rows ?? []);
          })
          .catch(() => {
            if (b.isConnected) {
              const text = b.querySelector(".channel-info .muted");
              if (text) text.textContent = "Guide unavailable";
            }
          });
        return b;
      },
    );
    collections.set(host.id, collection);
    if (!localItems.length)
      host.append(
        message(
          "No channels in this view",
          "Choose another category or clear the search.",
        ),
      );
    const first =
      localItems.find((m) => m.id === browseItem?.id) ?? localItems[0];
    if (first) {
      browseItem = first;
      void updateLivePane(pane, first, token);
    }
  }
  let queryTimer: ReturnType<typeof setTimeout>;
  search.addEventListener("input", () => {
    clearTimeout(queryTimer);
    queryTimer = setTimeout(list, 180);
  });
  renderTree();
  list();
  if (currentPlaying && preview) positionVideo(true);
}
function setChannelGuide(row: HTMLElement, rows: Programme[]) {
  const p = nowProgramme(rows);
  row.querySelector(".channel-info .muted")!.textContent = p
    ? `${p.title} · ${Math.max(1, Math.ceil((p.end - Date.now()) / 60000))} min left`
    : "No programme information";
  row.querySelector(".progress")?.replaceWith(progressBar(p));
}
async function updateLivePane(pane: HTMLElement, m: Media, token: number) {
  if (token !== view || browseItem?.id !== m.id) return;
  const paneFocus = pane.contains(document.activeElement)
    ? document.activeElement?.id
    : undefined;
  pane.replaceChildren();
  const stage = node("div", "live-stage");
  stage.id = "preview-stage";
  stage.append(
    image(m.image, "preview-logo"),
    node("span", "live-badge", "● LIVE"),
    action(
      "preview-play",
      "Watch " + m.name,
      () => play(m),
      "preview-play",
      "PLAY",
    ),
  );
  pane.append(stage, node("h2", "", m.name));
  if (paneFocus) focus.focus(paneFocus, false);
  if (
    state.settings().livePreview &&
    route === "live" &&
    overlay.hidden &&
    Date.now() - lastRemoteMove >= 600
  ) {
    if (currentPlaying?.id !== m.id || currentPlaying.kind !== "live") {
      if (currentPlaying) stopPlayback();
      currentPlaying = m;
      preview = true;
      playingSports = false;
      video.muted = !state.settings().previewSound;
      positionVideo(true);
      hud.hidden = true;
      void player.load(m, client.playback(m), 0, freeviewSource(m));
    } else {
      positionVideo(true);
      video.muted = !state.settings().previewSound;
    }
  }
  try {
    const rows = await guide(m.id);
    if (token !== view || browseItem?.id !== m.id) return;
    document.getElementById("channel-" + m.id) &&
      setChannelGuide(document.getElementById("channel-" + m.id)!, rows);
    const now = nowProgramme(rows);
    pane.querySelector("h2")!.textContent = now?.title ?? m.name;
    if (now)
      pane.append(
        node("p", "muted", `${nzTime(now.start)} – ${nzTime(now.end)}`),
        progressBar(now),
        node("p", "programme-description", now.description),
      );
    else
      pane.append(
        node(
          "p",
          "muted",
          "No programme information is available for this channel.",
        ),
      );
    const next = node("div", "next-programmes");
    rows
      .filter((p) => p.start > Date.now())
      .slice(0, 3)
      .forEach((p) =>
        next.append(node("p", "", `${nzTime(p.start)}  ${p.title}`)),
      );
    pane.append(
      next,
      action(
        "channel-schedule",
        "Full schedule",
        () => showSchedule(m),
        "",
        "EPG",
      ),
    );
  } catch (e) {
    if (token === view) pane.append(node("p", "error", (e as Error).message));
  }
}
async function showSchedule(m: Media) {
  const box = openOverlay(m.name + " · Schedule");
  const token = overlayVersion;
  box.append(
    node("p", "muted", "Loading programme guide…"),
    action("schedule-loading-close", "Close", closeOverlay),
  );
  focus.focus("schedule-loading-close", false);
  try {
    const rows = await guide(m.id);
    if (token !== overlayVersion) return;
    box.querySelector("p")?.remove();
    document.getElementById("schedule-loading-close")?.remove();
    rows.forEach((p, i) =>
      box.append(
        action(
          "schedule-" + i,
          `${nzTime(p.start)} – ${nzTime(p.end)}   ${p.title}`,
          () => {
            const info = openOverlay(p.title);
            info.append(
              node("p", "", p.description),
              action("programme-watch", "Watch " + m.name, () => play(m)),
              action("programme-close", "Close", closeOverlay),
            );
            focus.focus("programme-watch");
          },
          "schedule-row",
        ),
      ),
    );
    if (!rows.length)
      box.append(
        node("p", "muted", "No programme information is currently available."),
      );
    box.append(action("schedule-close", "Close", closeOverlay));
    focus.focus(rows.length ? "schedule-0" : "schedule-close");
  } catch (e) {
    if (token === overlayVersion) {
      box.append(
        node("p", "error", (e as Error).message),
        action("schedule-close", "Close", closeOverlay),
      );
      focus.focus("schedule-close");
    }
  }
}
function renderBrowse(main: HTMLElement, kind: "movie" | "series") {
  main.className = "browse";
  if (route !== "movies" && route !== "series") selected[kind] = undefined;
  const rows = catalogues[kind];
  header(main, kind === "movie" ? "Movies" : "Series");
  if (!rows) {
    unavailable(main, kind);
    return;
  }
  const backdrop = node("div", "browse-backdrop");
  backdrop.id = "browse-backdrop";
  main.prepend(backdrop);
  const spotlight = node("section", "browse-spotlight");
  spotlight.id = "browse-spotlight";
  main.append(spotlight);
  const toolbar = node("div", "browse-toolbar");
  const search = input(
    "browse-search",
    "Search " +
      (selected[kind]
        ? categories[kind]?.find((c) => c.id === selected[kind])?.label
        : "All " + (kind === "movie" ? "movies" : "series")),
  );
  let order = "provider";
  toolbar.append(
    action(
      "browse-all",
      kind === "movie" ? "All movies" : "All series",
      () => {
        selected[kind] = undefined;
        navigate(kind === "movie" ? "movies" : "series");
      },
      "tool" + (!selected[kind] ? " on" : ""),
      "LIST_GRID",
    ),
    search,
    action(
      "browse-sort",
      "Sort: Provider order",
      () => {
        order =
          order === "provider" ? "az" : order === "az" ? "za" : "provider";
        document.querySelector("#browse-sort span")!.textContent =
          "Sort: " +
          (order === "provider"
            ? "Provider order"
            : order === "az"
              ? "A–Z"
              : "Z–A");
        list();
      },
      "tool",
      "SORT",
    ),
    action(
      "browse-categories",
      "Categories",
      () => categoryPicker(kind),
      "tool",
      "LIST",
    ),
  );
  main.append(toolbar);
  const label =
    categories[kind]?.find((c) => c.id === selected[kind])?.label ??
    (kind === "movie" ? "All movies" : "All series");
  main.append(node("h2", "browse-category", label));
  const host = node("div", "vod-grid");
  host.id = "vod-grid";
  host.dataset.scroll = "";
  main.append(host);
  const token = view;
  function list() {
    collections.get(host.id)?.destroy();
    host.replaceChildren();
    let items = filtered(kind, search.value);
    if (order !== "provider")
      items = [...items].sort(
        (a, b) => (order === "az" ? 1 : -1) * a.name.localeCompare(b.name),
      );
    const col = new VirtualCollection(
      host,
      items.length,
      state.settings().vodColumns,
      444,
      (i) => {
        const m = items[i];
        const p = poster(m, "vod-" + m.id);
        p.addEventListener("focus", () => {
          browseItem = m;
          clearTimeout(hoverTimer);
          hoverTimer = setTimeout(
            () => void spotlightFor(m, spotlight, backdrop, token),
            750,
          );
        });
        return p;
      },
    );
    collections.set(host.id, col);
    if (items[0]) {
      browseItem = items[0];
      void spotlightFor(items[0], spotlight, backdrop, token, false);
    }
  }
  let queryTimer: ReturnType<typeof setTimeout>;
  search.addEventListener("input", () => {
    clearTimeout(queryTimer);
    queryTimer = setTimeout(list, 180);
  });
  list();
}
async function warmLibraries() {
  // All three complete catalogues are already in memory. Warm only the opening window,
  // never issue thousands of metadata requests or decode the entire poster library.
  const rows = [
    ...(catalogues.movie ?? []).slice(0, 12),
    ...(catalogues.series ?? []).slice(0, 12),
  ];
  await Promise.race([
    mapLimit(rows, 2, async (m) => {
      const saved = await deviceCache.get<{ at: number; details: Details }>(
        cacheAccount + ":details:" + key(m),
      );
      if (saved && Date.now() - saved.at < 7 * 86400000)
        detailCache.set(key(m), saved.details);
      const img = image(m.image);
      img.loading = "eager";
      try {
        await img.decode();
      } catch {}
    }),
    new Promise((r) => setTimeout(r, 1800)),
  ]);
}
async function getDetails(m: Media): Promise<Details> {
  const id = key(m),
    cached = detailCache.get(id);
  if (cached) return cached;
  const pending = detailsInFlight.get(id);
  if (pending) return pending;
  const generation = accountGeneration;
  const account = cacheAccount,
    source = client;
  const work = (async () => {
    const saved = await deviceCache.get<{ at: number; details: Details }>(
      account + ":details:" + id,
    );
    if (saved && Date.now() - saved.at < 7 * 86400000) return saved.details;
    const d = await source.details(m);
    void deviceCache.put(account + ":details:" + id, {
      at: Date.now(),
      details: d,
    });
    return d;
  })()
    .then((d) => {
      if (generation === accountGeneration) {
        detailCache.set(id, d);
        if (detailCache.size > 160)
          detailCache.delete(detailCache.keys().next().value!);
      }
      return d;
    })
    .finally(() => detailsInFlight.delete(id));
  detailsInFlight.set(id, work);
  return work;
}
async function spotlightFor(
  m: Media,
  spotlight: HTMLElement,
  backdrop: HTMLElement,
  token: number,
  enrich = true,
) {
  const apply = (item: Media) => {
    spotlight.replaceChildren(node("h2", "", item.name));
    const meta = [
      item.year,
      item.genre,
      item.rating > 0 ? `★ ${item.rating.toFixed(1)}` : "",
      ...(item.capabilities ?? []),
    ]
      .filter(Boolean)
      .join("  •  ");
    if (meta) spotlight.append(node("p", "metadata", meta));
    if (item.plot) spotlight.append(node("p", "overview", item.plot));
    backdrop.replaceChildren();
    if (item.backdrop && state.settings().backdrop)
      backdrop.append(image(item.backdrop, "browse-art"));
  };
  apply(m);
  if (!enrich || !state.settings().metadataOnFocus) return;
  try {
    const d = await getDetails(m);
    if (
      token === view &&
      browseItem?.id === m.id &&
      Date.now() - lastRemoteMove >= 650
    )
      apply(d.media);
  } catch {
    /* Browsing remains usable when metadata is temporarily unavailable. */
  }
}
async function openDetails(m: Media) {
  savePosition();
  const box = openOverlay(m.name, "details-modal");
  const token = overlayVersion;
  box.append(
    node("p", "muted", "Loading details…"),
    action("details-close", "Close", closeOverlay),
  );
  focus.focus("details-close");
  try {
    const pending = getDetails(m);
    const d: Details =
      m.kind === "movie"
        ? { media: { ...m }, episodes: [], seasons: [], cast: "" }
        : await pending;
    if (token !== overlayVersion) return;
    box.replaceChildren();
    if (d.media.backdrop)
      box.append(image(d.media.backdrop, "details-backdrop"));
    const info = node("div", "details-copy");
    info.append(
      node("h1", "", d.media.name),
      node(
        "p",
        "metadata",
        [
          d.media.year,
          d.media.genre,
          d.media.rating ? `★ ${d.media.rating}` : "",
          d.media.duration ? duration(d.media.duration) : "",
        ]
          .filter(Boolean)
          .join("  •  "),
      ),
      node("p", "overview", d.media.plot ?? ""),
    );
    if (d.cast) info.append(node("p", "muted", "Cast: " + d.cast));
    const actions = node("div", "actions");
    if (m.kind === "movie") {
      const progress = state.progressFor(m);
      if (progress && !progress.complete && progress.position > 15)
        actions.append(
          action(
            "detail-resume",
            "Resume · " + duration(progress.position),
            () => play(d.media, progress.position),
            "primary",
            "PLAY",
          ),
        );
      actions.append(
        action(
          "detail-play",
          "Play from start",
          () => play(d.media, 0),
          "",
          "PLAY",
        ),
      );
    }
    actions.append(
      action(
        "detail-save",
        state.saved(m) ? "Remove from Saved" : "Add to Saved",
        () => {
          state.toggle(d.media);
          document.querySelector("#detail-save span")!.textContent =
            state.saved(m) ? "Remove from Saved" : "Add to Saved";
        },
        "",
        "FAVORITE",
      ),
      action("detail-close", "Back", closeOverlay, "", "CHEVRON_LEFT"),
    );
    info.append(actions);
    box.append(info);
    if (m.kind === "series") {
      const seasonBar = node("div", "seasons");
      const list = node("div", "episodes");
      list.id = "episode-list";
      const renderSeason = (season: number) => {
        collections.get(list.id)?.destroy();
        list.replaceChildren();
        list.scrollTop = 0;
        const episodes = d.episodes.filter((e) => e.season === season);
        const episodeCollection = new VirtualCollection(
          list,
          episodes.length,
          1,
          120,
          (i) => {
            const ep = episodes[i];
            const progress = state.progressFor(ep);
            const b = action(
              "episode-" + ep.id,
              `${ep.episode}. ${ep.name}${progress && !progress.complete && progress.position > 15 ? " · Resume " + duration(progress.position) : ""}`,
              () => {
                episodeQueue = d.episodes;
                play(
                  ep,
                  progress && !progress.complete ? progress.position : 0,
                );
              },
              "episode",
            );
            b.append(image(ep.image, "episode-image"));
            if (ep.plot) b.append(node("small", "muted", ep.plot));
            b.dataset.media = key(ep);
            return b;
          },
        );
        collections.set(list.id, episodeCollection);
      };
      d.seasons.forEach((s) =>
        seasonBar.append(
          action(
            "season-" + s,
            "Season " + s,
            () => {
              renderSeason(s);
              focus.focus(
                "episode-" + d.episodes.find((e) => e.season === s)?.id,
              );
            },
            "chip",
          ),
        ),
      );
      box.append(seasonBar, list);
      if (d.seasons[0] !== undefined) renderSeason(d.seasons[0]);
      else
        list.append(
          message(
            "Episodes unavailable",
            "This series does not currently have available episodes.",
          ),
        );
    }
    if (m.kind === "movie")
      void pending
        .then((full) => {
          if (token !== overlayVersion) return;
          Object.assign(d.media, full.media);
          const title = info.querySelector("h1"),
            meta = info.querySelector(".metadata"),
            plot = info.querySelector(".overview");
          if (title) title.textContent = full.media.name;
          if (meta)
            meta.textContent = [
              full.media.year,
              full.media.genre,
              full.media.rating ? `★ ${full.media.rating}` : "",
              full.media.duration ? duration(full.media.duration) : "",
            ]
              .filter(Boolean)
              .join(" • ");
          if (plot) plot.textContent = full.media.plot ?? "";
          if (full.media.backdrop && !box.querySelector(".details-backdrop"))
            box.prepend(image(full.media.backdrop, "details-backdrop"));
        })
        .catch(() => {
          /* Prepared movie controls remain available when extra metadata fails. */
        });
    focus.focus(
      m.kind === "movie"
        ? document.getElementById("detail-resume")
          ? "detail-resume"
          : "detail-play"
        : d.seasons.length
          ? "season-" + d.seasons[0]
          : "detail-save",
    );
  } catch (e) {
    if (token === overlayVersion) {
      box.querySelector("p")!.textContent = (e as Error).message;
      box.append(action("details-retry", "Try again", () => openDetails(m)));
      focus.focus("details-retry");
    }
  }
}
function eventCard(e: SportEvent) {
  const b = action("event-" + e.id, "", () => openEvent(e), "event-card");
  b.dataset.event = e.id;
  const top = node("div", "event-top");
  top.append(
    node("span", "muted", sportLabel(e.sport) + " · " + e.leagueName),
    node("b", "", e.state === "live" ? "LIVE" : nzTime(e.startUtcMs)),
  );
  b.append(top);
  for (const t of ["racing", "golf", "tennis", "mma", "boxing"].includes(
    e.sport,
  )
    ? []
    : e.competitors.slice(0, 2)) {
    const team = node("div", "event-team");
    team.append(
      image(teamCrest(e, t), "crest"),
      node("b", "", t.shortName || t.name),
    );
    if (t.score) team.append(node("strong", "score", t.score));
    b.append(team);
  }
  if (!e.competitors.length) b.append(node("h3", "", e.title));
  if (["racing", "golf", "tennis", "mma", "boxing"].includes(e.sport))
    b.append(node("h3", "event-title", e.editorial_title || e.title));
  b.append(
    node(
      "div",
      "event-bottom",
      e.broadcasts.map((b) => b.name).join(" · ") ||
        "Find channels in Match Centre",
    ),
  );
  return b;
}
function renderSports(main: HTMLElement) {
  main.className = "sports";
  header(main, "SPORT LOUNGE");
  main.append(
    node(
      "p",
      "sports-subtitle",
      "Live fixtures, today’s schedule and what comes next",
    ),
  );
  if (sportsLoading)
    main.append(
      node("p", "sports-loading", "Loading connected sports sources…"),
    );
  const time = node("div", "sport-time");
  for (const [id, label] of [
    ["live", "Live now"],
    ["today", "Today"],
    ["upcoming", "Upcoming"],
  ])
    time.append(
      action(
        "time-" + id,
        label + " " + selectEvents(events, id, "all").length,
        () => {
          sportTime = id;
          navigate("sports");
        },
        "chip" + (sportTime === id ? " on" : ""),
      ),
    );
  main.append(time);
  const filter = node("div", "sport-filters");
  filter.append(
    action(
      "teams-filter",
      "My teams",
      () => {
        myTeams = !myTeams;
        navigate("sports");
      },
      "chip" + (myTeams ? " on" : ""),
      "STAR",
    ),
    action(
      "sport-all",
      "All Sports",
      () => {
        sportFilter = "all";
        navigate("sports");
      },
      "chip" + (sportFilter === "all" ? " on" : ""),
    ),
  );
  for (const s of [...new Set(events.map((e) => e.sport))])
    filter.append(
      action(
        "sport-" + s,
        sportLabel(s),
        () => {
          sportFilter = s;
          navigate("sports");
        },
        "chip" + (sportFilter === s ? " on" : ""),
      ),
    );
  filter.append(
    action(
      "sport-refresh",
      sportsLoading ? "Loading…" : "Refresh",
      async () => {
        if (sportsLoading) return;
        await loadEvents();
        if (route === "sports" && overlay.hidden) navigate("sports", true);
      },
      "chip",
      "REFRESH",
    ),
  );
  main.append(filter);
  const items = selectEvents(events, sportTime, sportFilter).filter(
    (e) => !myTeams || e.competitors.some((t) => state.teams().includes(t.id)),
  );
  const host = node("div", "sports-grid");
  host.id = "sports-grid";
  host.dataset.scroll = "";
  main.append(host);
  if (!items.length) {
    host.append(
      message(
        errors.sports ? "Schedule unavailable" : "No events in this view",
        errors.sports ??
          (myTeams
            ? "Follow teams from an event to see them here."
            : "Select Today or Upcoming to see more fixtures."),
      ),
    );
    return;
  }
  const col = new VirtualCollection(host, items.length, 3, 244, (i) =>
    eventCard(items[i]),
  );
  collections.set(host.id, col);
}
async function openEvent(e: SportEvent) {
  const box = openOverlay(e.editorial_title || e.title, "event-modal");
  const token = overlayVersion;
  box.append(
    node(
      "p",
      "metadata",
      `${sportLabel(e.sport)} · ${e.leagueName} · ${nzDate(e.startUtcMs)} · ${nzTime(e.startUtcMs)} NZ time`,
    ),
  );
  if (e.venue) box.append(node("p", "muted", e.venue));
  if (e.subtitle) box.append(node("p", "", e.subtitle));
  const actions = node("div", "actions");
  actions.append(
    action(
      "event-remind",
      state.reminders().some((x) => x.id === e.id)
        ? "Reminder set"
        : "Remind me",
      () => {
        const set = state.toggleReminder(e);
        document.querySelector("#event-remind span")!.textContent = set
          ? "Reminder set"
          : "Remind me";
      },
      "",
      "BELL",
    ),
  );
  e.competitors.forEach((t) =>
    actions.append(
      action(
        "follow-" + t.id,
        (state.teams().includes(t.id) ? "Following " : "Follow ") + t.shortName,
        () => {
          state.toggleTeam(t.id);
          document.querySelector(
            "#follow-" + CSS.escape(t.id) + " span",
          )!.textContent =
            (state.teams().includes(t.id) ? "Following " : "Follow ") +
            t.shortName;
        },
        "",
        "STAR",
      ),
    ),
  );
  actions.append(action("event-close", "Back", closeOverlay));
  box.append(actions, node("h3", "", "Watch options"));
  const list = node("div", "watch-options");
  list.append(node("p", "muted", "Loading channels from Lounge TV…"));
  box.append(list);
  focus.focus("event-remind");
  try {
    const rows = await client.candidates(e);
    if (token !== overlayVersion) return;
    list.replaceChildren();
    if (!rows.length) {
      list.append(
        message(
          "No channels found yet",
          "Lounge TV has not returned a channel for this event. Check again closer to the start.",
        ),
      );
      return;
    }
    list.append(
      node(
        "p",
        "muted",
        "Channel matches are supplied by the current Lounge TV sports service. Playback has not been verified on this TV.",
      ),
    );
    rows.forEach((r, i) => {
      const b = action(
        "watch-option-" + i,
        `${r.media.name} · ${r.confidence}`,
        () => play(r.media, 0, true),
        "watch-option",
        "PLAY",
      );
      list.append(b);
    });
  } catch (err) {
    if (token === overlayVersion)
      list.replaceChildren(
        node("p", "error", (err as Error).message),
        action("event-retry", "Try again", () => openEvent(e)),
      );
  }
}
function renderSaved(main: HTMLElement) {
  main.className = "saved";
  header(main, "Saved");
  const tabs = node("div", "saved-tabs");
  tabs.append(
    action(
      "saved-favourites",
      "Favourites",
      () => {
        savedTab = "favourites";
        navigate("saved");
      },
      "chip" + (savedTab === "favourites" ? " on" : ""),
    ),
    action(
      "saved-reminders",
      `Reminders (${state.reminders().length})`,
      () => {
        savedTab = "reminders";
        navigate("saved");
      },
      "chip" + (savedTab === "reminders" ? " on" : ""),
    ),
  );
  main.append(tabs);
  const scroller = node("div", "saved-scroll");
  scroller.id = "saved-scroll";
  scroller.dataset.scroll = "";
  main.append(scroller);
  if (savedTab === "reminders") {
    const rows = state.reminders();
    if (!rows.length)
      scroller.append(
        message("No reminders yet", "Save a sporting event to find it here."),
      );
    else rows.forEach((e) => scroller.append(eventCard(e)));
    return;
  }
  const favourites = state.favourites();
  main.append(node("p", "saved-hint", "Hold OK to remove an item from Saved."));
  if (!favourites.length) {
    scroller.append(
      message(
        "Your favourites live here",
        "Hold OK on a channel, movie or series to save it.",
      ),
    );
    return;
  }
  const live = favourites.filter((m) => m.kind === "live"),
    sport = live.filter((m) =>
      /sport|espn|bein|ufc|nfl|nba|cricket|rugby|tnt|dazn/i.test(m.name),
    ),
    kids = live.filter((m) =>
      /kids|nick|disney|cartoon|cbeebies|junior/i.test(m.name),
    );
  const groups: [string, Media[]][] = [
    ["Sport", sport],
    ["Kids & Family", kids],
    ["Movies", favourites.filter((m) => m.kind === "movie")],
    ["Series", favourites.filter((m) => m.kind === "series")],
    [
      "Entertainment",
      live.filter((m) => !sport.includes(m) && !kids.includes(m)),
    ],
  ];
  groups.forEach(([label, rows], i) => {
    if (rows.length)
      rail(scroller, label, rows, "saved-" + i, rows[0].kind === "live");
  });
}
function renderSearch(main: HTMLElement) {
  main.className = "search";
  header(main, "Search");
  const q = input("global-search", "Search movies, series and live TV");
  q.value = searchQuery;
  main.append(q);
  const tabs = node("div", "search-tabs");
  let filter = "all";
  for (const [id, label] of [
    ["all", "All"],
    ["movie", "Movies"],
    ["series", "Series"],
    ["live", "Live TV"],
  ])
    tabs.append(
      action(
        "search-" + id,
        label,
        () => {
          filter = id;
          results();
        },
        "chip",
      ),
    );
  main.append(tabs);
  const host = node("div", "search-results");
  host.id = "search-results";
  host.dataset.scroll = "";
  main.append(host);
  let timer: ReturnType<typeof setTimeout>;
  const results = () => {
    collections.get(host.id)?.destroy();
    host.replaceChildren();
    if (!searchQuery.trim()) {
      host.append(
        message("Find something to watch", "Search your Lounge TV catalogue."),
      );
      return;
    }
    const query = searchQuery.trim().toLowerCase();
    const rows = (["movie", "series", "live"] as Kind[])
      .flatMap((k) => catalogues[k] ?? [])
      .filter(
        (m) =>
          (filter === "all" || m.kind === filter) &&
          m.name.toLowerCase().includes(query),
      );
    if (!rows.length) {
      host.append(message("No results", "Try another title or channel name."));
      return;
    }
    const collection = new VirtualCollection(host, rows.length, 6, 390, (i) =>
      poster(rows[i], "result-" + rows[i].kind + "-" + rows[i].id),
    );
    collections.set(host.id, collection);
  };
  q.addEventListener("input", () => {
    searchQuery = q.value;
    clearTimeout(timer);
    timer = setTimeout(results, 200);
  });
  results();
}
const quickDefaults = [
  "vp_live_preview",
  "vp_preview_audio",
  "vp_channel_numbers",
  "vp_autoplay",
];
const settingScreen = new SettingsScreen({
  control: settingControl,
  focus: (id) => focus.focus(id),
  pins: () => state.read<string[]>("quick-pins", quickDefaults),
  savePins: (keys) => state.write("quick-pins", keys),
  menu: (key) => {
    const keys = state.read<string[]>("quick-pins", quickDefaults),
      has = keys.includes(key);
    const box = openOverlay("Quick settings");
    box.append(
      action("setting-pin", has ? "Unpin from Quick" : "Pin to Quick", () => {
        state.write(
          "quick-pins",
          has ? keys.filter((k) => k !== key) : [...keys, key],
        );
        closeOverlay();
        settingScreen.render(document.getElementById("main")!);
        focus.focus("setting-" + key);
      }),
      action("setting-pin-close", "Cancel", closeOverlay),
    );
    focus.focus("setting-pin");
  },
});
function renderSettings(main: HTMLElement) {
  settingScreen.render(main);
}
function applyPreferences() {
  const s = state.settings();
  document.body.dataset.motion = String(s.animations);
  document.body.dataset.backdrop = String(s.backdrop);
  document.documentElement.style.setProperty("--blue", s.accent);
  document.documentElement.style.setProperty(
    "--text-scale",
    String(s.fontSize / 100),
  );
  video.style.objectFit = s.aspect;
  video.volume = Math.max(0, Math.min(1, s.volume / 100));
  player.configure({
    startupTimeout: s.tuneTimeout * 1000,
    reconnects: s.reconnects,
  });
}
function settingControl(id: string): SettingControl | undefined {
  const aliases: Record<string, string> = {
    quick_live_preview: "vp_live_preview",
    quick_preview_sound: "vp_preview_audio",
    quick_channel_numbers: "vp_channel_numbers",
    quick_hdr: "vp_hdr",
    quick_autoplay: "vp_autoplay",
    quick_check_update: "update_startup",
    profiles_title: "profiles",
    settings_profile_add: "profiles",
    navigation: "navigation",
    live_layout: "live_layout",
  };
  id = aliases[id] ?? id;
  const settings = state.settings();
  const toggle = (
    key: keyof typeof settings,
    help?: string,
  ): SettingControl => ({
    value: settings[key] ? "On" : "Off",
    help,
    run: () => {
      state.setSettings({ ...state.settings(), [key]: !state.settings()[key] });
      applyPreferences();
    },
  });
  const choice = (
    key: keyof typeof settings,
    values: (string | number)[],
    label = (v: any) => String(v),
  ): SettingControl => ({
    value: label(settings[key]),
    run: () => {
      const box = openOverlay(
        SETTINGS_ROWS.find((r) => r.key === id)?.title ?? "Choose",
      );
      for (const [i, v] of values.entries())
        box.append(
          action(
            "setting-choice-" + i,
            label(v) + (settings[key] === v ? " · Selected" : ""),
            () => {
              state.setSettings({
                ...state.settings(),
                [key]: v,
                ...(key === "subtitleLanguage"
                  ? { subtitles: v !== "off" }
                  : {}),
              });
              applyPreferences();
              closeOverlay();
              settingScreen.render(document.getElementById("main")!);
              focus.focus("setting-" + id);
            },
          ),
        );
      box.append(action("setting-choice-close", "Cancel", closeOverlay));
      focus.focus(
        "setting-choice-" +
          Math.max(0, values.indexOf(settings[key] as string | number)),
      );
    },
  });
  const open = (
    value: string,
    run: () => void,
    help?: string,
  ): SettingControl => ({ value, run, help });
  if (id === "vp_autoplay") return toggle("autoplayNext");
  if (id === "vp_preview_audio")
    return toggle(
      "previewSound",
      "Audio is enabled only while Live preview is running.",
    );
  if (id === "vp_live_preview")
    return toggle(
      "livePreview",
      "Play the focused channel in the Live preview after the remote has settled. Off avoids tuning streams while browsing.",
    );
  if (id === "vp_channel_numbers") return toggle("channelNumbers");
  if (id === "vp_resume") return toggle("resume");
  if (id === "animations") return toggle("animations");
  if (id === "reminders") return toggle("remindersEnabled");
  if (id === "reminder_lead")
    return choice("reminderLead", [1, 5, 10, 15, 30], (v) => v + " minutes");
  if (id === "vp_zoom")
    return choice(
      "aspect",
      ["contain", "cover", "fill"],
      (v) =>
        ({ contain: "Fit", cover: "Fill screen", fill: "Stretch" })[
          v as string
        ] ?? v,
    );
  if (id === "vp_reset_zoom")
    return open("Reset", () => {
      state.setSettings({ ...settings, aspect: "contain" });
      applyPreferences();
      toast("Picture fit reset");
    });
  if (id === "accent")
    return choice("accent", ["#0084ff", "#00cbb5", "#a57bff", "#f0b342"]);
  if (id === "fonts")
    return choice("fontSize", [90, 100, 110, 120], (v) => v + "%");
  if (id === "vod_layout")
    return choice("vodColumns", [5, 6, 7], (v) => v + " columns");
  if (id === "epg_offset")
    return choice("epgOffset", [-180, -120, -60, 0, 60, 120, 180], (v) =>
      v === 0 ? "No shift" : (v > 0 ? "+" : "") + v + " minutes",
    );
  if (id === "guide_days")
    return choice("guideDays", [1, 3, 7, 8], (v) => v + " days (next refresh)");
  if (id === "vp_volume")
    return choice("volume", [0, 25, 50, 75, 100], (v) => v + "%");
  if (id === "vp_reset_volume")
    return open("Reset", () => {
      state.setSettings({ ...settings, volume: 100 });
      applyPreferences();
    });
  if (id === "vp_audio_lang")
    return choice("audioLanguage", ["en", "mi", "fr", "es", "de"]);
  if (id === "vp_sub_lang")
    return choice("subtitleLanguage", ["off", "en", "mi", "fr", "es", "de"]);
  if (id === "vp_seek_step")
    return choice("seekStep", [5, 10, 15, 30, 60], (v) => v + " seconds");
  if (id === "vp_tune_timeout")
    return choice("tuneTimeout", [15, 22, 30, 45], (v) => v + " seconds");
  if (id === "vp_vod_reconnects")
    return choice(
      "reconnects",
      [0, 1, 2, 4, 6],
      (v) => v + " retries per outage",
    );
  if (id === "app_startup")
    return choice("startup", ["home", "live", "movies", "series", "sports"]);
  if (id === "tab_sources" || id === "playlists")
    return open(state.playlists().length + " playlists", playlistSettings);
  if (id === "tab_epg")
    return open(
      guideSources().filter((s) => s.enabled).length + " sources",
      epgSettings,
    );
  if (id === "profiles")
    return open(state.profiles().length + " profiles", profileSettings);
  if (id === "profiles_delete_title") return open("Manage", profileSettings);
  if (id === "tab_sports")
    return open(events.length + " fixtures", () => navigate("sports"));
  if (id === "vp_measured_stats" || id === "vp_logging" || id === "diagnostics")
    return open(player.trace.length + " entries", showDiagnostics);
  if (id === "check_updates")
    return open(
      "Manual install",
      () => {
        const b = openOverlay("App updates");
        b.append(
          node("p", "", "Installed version: 0.1.11"),
          node(
            "p",
            "",
            "This test app updates by installing the latest Lounge TV LG package from your computer using the existing TV connection. Keep the app installed when updating.",
          ),
          node(
            "p",
            "muted",
            "Automatic app updates are not enabled in this build. Playlists and EPG refresh separately in Sources & guide.",
          ),
          action("update-close", "Close", closeOverlay),
        );
        focus.focus("update-close");
      },
      "How to update this LG test app.",
    );
  if (id === "about")
    return open("0.1.11", () => {
      const b = openOverlay("Lounge TV for LG");
      b.append(
        node("p", "", "QA 0.1.11 · Calendar crest data fix"),
        node(
          "p",
          "muted",
          "Built for LG webOS. Settings show the features available on this TV.",
        ),
        action("about-close", "Close", closeOverlay),
      );
      focus.focus("about-close");
    });
  if (id === "saved")
    return open(state.favourites().length + " saved", () => navigate("saved"));
  if (id === "history")
    return open(state.progress().length + " titles", () =>
      confirm("Clear watch history for " + state.profileName + "?", () => {
        state.clearHistory();
        closeOverlay();
        toast("Watch history cleared");
      }),
    );
  if (id === "backup")
    return open(
      state.read("local-backup", null) ? "Saved on this TV" : "Never",
      backupSettings,
    );
  if (id === "tab_metadata")
    return open(
      settings.metadataOnFocus ? "On focus" : "On selection",
      () => {
        const b = openOverlay("Metadata");
        b.append(
          action(
            "metadata-focus",
            "Load details while browsing · " +
              (settings.metadataOnFocus ? "On" : "Off"),
            () => {
              state.setSettings({
                ...state.settings(),
                metadataOnFocus: !state.settings().metadataOnFocus,
              });
              closeOverlay();
            },
          ),
          action(
            "metadata-backdrop",
            "Browse backdrops · " + (settings.backdrop ? "On" : "Off"),
            () => {
              state.setSettings({
                ...state.settings(),
                backdrop: !state.settings().backdrop,
              });
              applyPreferences();
              closeOverlay();
            },
          ),
          action(
            "metadata-rating",
            "Ratings · " + (settings.showRatings ? "On" : "Off"),
            () => {
              state.setSettings({
                ...state.settings(),
                showRatings: !state.settings().showRatings,
              });
              closeOverlay();
            },
          ),
          action("metadata-close", "Close", closeOverlay),
        );
        focus.focus("metadata-focus");
      },
      "Metadata and large artwork load on selection by default to keep LG navigation responsive.",
    );
  if (id === "tab_customize")
    return open("Local category visibility", customizeSettings);
  if (id === "browsing_lists")
    return choice("vodColumns", [5, 6, 7], (v) => v + " columns");
  if (id === "tab_home")
    return choice("startup", ["home", "live", "movies", "series", "sports"]);
  if (id === "vp_live_engine" || id === "vp_vod_engine" || id === "vp_hw")
    return {
      value: "LG native",
      supported: false,
      run: () => {},
      help: "LG native playback uses the TV hardware decoder. Android ExoPlayer and MPV are not included in this webOS build. Changing engines requires a separate webOS implementation.",
    };
  if (id === "theme")
    return {
      value: "Lounge dark",
      supported: false,
      run: () => {},
      help: "The LG app currently uses the Lounge dark theme. Additional Android themes have not been ported.",
    };
  if (id === "navigation")
    return {
      value: "Icons",
      supported: false,
      run: () => {},
      help: "The sidebar stays icons-only, as requested for LG.",
    };
  return undefined;
}
function playlistSettings() {
  const b = openOverlay("Playlists");
  for (const playlist of state.playlists())
    b.append(
      action(
        "playlist-switch-" + playlist.auth.username,
        playlist.name +
          (playlist.auth.username === activeAuth?.username ? " · In use" : ""),
        () => {
          closeOverlay();
          void startSession(playlist.auth);
        },
      ),
    );
  b.append(
    action("playlist-add", "Add a Lounge TV playlist", addPlaylist),
    node("h2", "", state.profileName + " · Lounge TV"),
    node(
      "p",
      "muted",
      `${catalogues.live?.length ?? 0} channels · ${catalogues.movie?.length ?? 0} movies · ${catalogues.series?.length ?? 0} series`,
    ),
    action("playlist-refresh", "Refresh playlist and guide", () => {
      closeOverlay();
      if (activeAuth) void startSession(activeAuth, true);
    }),
    action(
      "playlist-remember",
      "Remember login · " + (state.settings().rememberLogin ? "On" : "Off"),
      () => {
        const s = state.settings();
        s.rememberLogin = !s.rememberLogin;
        state.setSettings(s);
        state.remember(s.rememberLogin ? (activeAuth ?? null) : null);
        if (!s.rememberLogin && activeAuth)
          state.forgetPlaylist(activeAuth.username);
        closeOverlay();
        playlistSettings();
      },
    ),
    action("playlist-login", "Change playlist login", () =>
      confirm("Change Lounge TV login?", () => {
        stopPlayback();
        state.remember(null);
        closeOverlay();
        login();
      }),
    ),
    action("playlist-remove", "Remove saved playlist login", () =>
      confirm("Remove this saved login?", () => {
        if (activeAuth) state.forgetPlaylist(activeAuth.username);
        state.remember(null);
        closeOverlay();
        toast("Saved login removed. Current session stays active.");
      }),
    ),
    action("playlist-close", "Close", closeOverlay),
  );
  focus.focus("playlist-refresh");
}
function addPlaylist() {
  const b = openOverlay("Add Lounge TV playlist"),
    name = input("playlist-name", "Playlist name"),
    user = input("playlist-user", "Username"),
    pass = input("playlist-password", "Password", true),
    error = node("p", "error");
  b.append(
    name,
    user,
    pass,
    error,
    action("playlist-add-save", "Sign in and prepare playlist", async () => {
      const auth = { username: user.value.trim(), password: pass.value };
      if (!auth.username || !auth.password) {
        error.textContent = "Enter username and password.";
        return;
      }
      try {
        await new LoungeClient(auth).login();
        state.savePlaylist(auth, name.value.trim());
        closeOverlay();
        await startSession(auth);
      } catch {
        error.textContent =
          "Unable to sign in. Check the playlist credentials.";
      }
    }),
    action("playlist-add-cancel", "Cancel", closeOverlay),
  );
  focus.focus(name.id);
}
function epgSettings() {
  const b = openOverlay("EPG sources");
  b.append(
    node(
      "p",
      "muted",
      "Source changes apply when you refresh the guide. Additional feeds must permit HTTPS XMLTV access from TV apps.",
    ),
  );
  b.append(
    node(
      "p",
      "muted",
      guideSummary
        ? `${guideSummary.currentChannels ?? guideSummary.channels} channels with programmes now in refreshed data / ${guideSummary.totalChannels ?? catalogues.live?.length ?? 0} · ${guideSummary.channels} matched guide channels · ${guideSummary.records.toLocaleString()} programmes · Updated ${nzDate(guideSummary.at)} ${nzTime(guideSummary.at)}${guideSummary.failedSources ? " · " + guideSummary.failedSources + " sources failed" : ""}${guideSummary.fallbackGeneration ? " · Previous valid guide retained for missing entries" : ""}`
        : "Guide not yet loaded",
    ),
  );
  for (const result of guideSummary?.sourceStats ?? [])
    b.append(
      node(
        "p",
        "muted",
        `${result.name}: ${result.status === "ready" ? result.currentChannels + " channels with programmes now" : "Unavailable; other guides retained"}`,
      ),
    );
  for (const source of guideSources())
    b.append(
      action(
        "epg-source-" + source.id,
        source.name + " · " + (source.enabled ? "On" : "Off"),
        () => {
          if (source.id === "lounge")
            state.write("guide-canonical-enabled", !source.enabled);
          else if (source.builtin) {
            const disabled = state.read<string[]>(
              "guide-builtins-disabled",
              [],
            );
            state.write(
              "guide-builtins-disabled",
              source.enabled
                ? [...disabled, source.id]
                : disabled.filter((id) => id !== source.id),
            );
          } else
            state.write(
              "guide-sources",
              state
                .read<GuideSource[]>("guide-sources", [])
                .map((s) =>
                  s.id === source.id ? { ...s, enabled: !s.enabled } : s,
                ),
            );
          state.write("guide-client-revision", 0);
          closeOverlay();
          epgSettings();
        },
      ),
      ...(source.builtin
        ? []
        : [
            action("epg-remove-" + source.id, "Remove " + source.name, () => {
              state.write(
                "guide-sources",
                state
                  .read<GuideSource[]>("guide-sources", [])
                  .filter((s) => s.id !== source.id),
              );
              closeOverlay();
              epgSettings();
            }),
          ]),
    );
  b.append(
    action("epg-add", "Add XMLTV source", addEpgSource),
    action("epg-sync", "Refresh EPG now", async () => {
      const status = node("p", "muted", "Preparing guide…");
      b.append(status);
      try {
        await loadFullGuide((text) => (status.textContent = text));
        epgCache.clear();
        status.textContent = `${guideSummary?.channels ?? 0} channels ready${guideSummary?.failedSources ? " · " + guideSummary.failedSources + " sources failed" : ""}`;
        toast("Programme guide refreshed");
      } catch {
        status.textContent = "Guide refresh failed. Previous guide retained.";
      }
    }),
    action("epg-match", "Match a channel to EPG", matchGuideSettings),
    action(
      "epg-refresh-hours",
      "Refresh on startup after · " +
        state.settings().guideRefreshHours +
        " hours",
      () => {
        const s = state.settings();
        s.guideRefreshHours =
          s.guideRefreshHours === 6 ? 12 : s.guideRefreshHours === 12 ? 24 : 6;
        state.setSettings(s);
        closeOverlay();
        epgSettings();
      },
    ),
    action("epg-close", "Close", closeOverlay),
  );
  focus.focus("epg-source-lounge");
}
function addEpgSource() {
  const b = openOverlay("Add XMLTV source"),
    name = input("epg-name", "Source name"),
    url = input("epg-url", "https://… XMLTV URL"),
    error = node("p", "error");
  b.append(
    name,
    url,
    error,
    action("epg-save", "Save source", () => {
      try {
        const u = new URL(url.value);
        if (u.protocol !== "https:") throw Error();
        const sources = state.read<GuideSource[]>("guide-sources", []);
        if (sources.length >= 5) {
          error.textContent = "Up to five additional sources are supported.";
          return;
        }
        state.write("guide-sources", [
          ...sources,
          {
            id: "epg" + Date.now(),
            name: name.value.trim() || "Programme guide",
            url: u.toString(),
            enabled: true,
          },
        ]);
        state.write("guide-client-revision", 0);
        closeOverlay();
        epgSettings();
      } catch {
        error.textContent = "Enter a valid HTTPS XMLTV address.";
      }
    }),
    action("epg-add-close", "Cancel", closeOverlay),
  );
  focus.focus(name.id);
}
function matchGuideSettings() {
  const b = openOverlay("Match channel EPG"),
    search = input("epg-match-search", "Find a channel"),
    results = node("div");
  b.append(search, results, action("epg-match-close", "Close", closeOverlay));
  const draw = () => {
    results.replaceChildren();
    if (!search.value.trim()) return;
    const matches = (catalogues.live ?? [])
      .filter((m) => m.name.toLowerCase().includes(search.value.toLowerCase()))
      .slice(0, 12);
    for (const m of matches)
      results.append(
        action("epg-channel-" + m.id, m.name, () => {
          const form = openOverlay("EPG match · " + m.name),
            id = input("epg-match-id", "Exact XMLTV channel ID");
          id.value =
            state.read<Record<string, string>>("guide-matches", {})[m.id] ??
            m.epgId ??
            "";
          form.append(
            id,
            action("epg-match-save", "Save and refresh guide", async () => {
              state.write("guide-matches", {
                ...state.read<Record<string, string>>("guide-matches", {}),
                [m.id]: id.value.trim(),
              });
              state.write("guide-client-revision", 0);
              const status = node("p", "muted", "Refreshing…");
              form.append(status);
              try {
                await loadFullGuide((t) => (status.textContent = t));
                epgCache.clear();
                closeOverlay();
                toast("Channel guide updated");
              } catch {
                status.textContent = "Refresh failed. Match saved for retry.";
              }
            }),
            action("epg-match-reset", "Use playlist ID", () => {
              const ids = state.read<Record<string, string>>(
                "guide-matches",
                {},
              );
              delete ids[m.id];
              state.write("guide-matches", ids);
              state.write("guide-client-revision", 0);
              closeOverlay();
            }),
            action("epg-match-cancel", "Cancel", closeOverlay),
          );
          focus.focus(id.id);
        }),
      );
  };
  search.addEventListener("input", draw);
  focus.focus(search.id);
}
function profileSettings() {
  const b = openOverlay("Profiles");
  for (const p of state.profiles())
    b.append(
      action(
        "profile-edit-" + p.id,
        p.name + (p.id === state.profile ? " · In use" : ""),
        () => {
          const form = openOverlay("Edit profile"),
            name = input("profile-edit-name", "Profile name");
          name.value = p.name;
          form.append(
            name,
            action("profile-edit-save", "Save name", () => {
              if (!name.value.trim()) return;
              state.write(
                "profiles",
                state
                  .profiles()
                  .map((x) =>
                    x.id === p.id
                      ? { ...x, name: name.value.trim().slice(0, 24) }
                      : x,
                  ),
              );
              closeOverlay();
              profileSettings();
            }),
            action("profile-edit-switch", "Use this profile", () => {
              state.switchProfile(p.id);
              closeOverlay();
              navigate("home");
            }),
            ...(state.profiles().length > 1
              ? [
                  action("profile-edit-delete", "Delete profile", () =>
                    confirm("Delete " + p.name + "?", () => {
                      const rows = state
                        .profiles()
                        .filter((x) => x.id !== p.id);
                      state.write("profiles", rows);
                      if (state.profile === p.id)
                        state.switchProfile(rows[0].id);
                      closeOverlay();
                      profileSettings();
                    }),
                  ),
                ]
              : []),
            action("profile-edit-close", "Close", closeOverlay),
          );
          focus.focus(name.id);
        },
      ),
    );
  b.append(
    action("profile-settings-add", "Add a profile", profiles),
    action("profile-settings-close", "Close", closeOverlay),
  );
  focus.focus("profile-edit-" + state.profile);
}
function backupSettings() {
  const b = openOverlay("Backup & Restore");
  b.append(
    node(
      "p",
      "muted",
      "Save one local snapshot on this TV. Includes profiles, favorites, watch history and preferences. Login credentials are excluded. This does not protect against uninstalling or resetting the TV.",
    ),
    action("backup-save", "Create local backup", () => {
      const profiles = state.profiles();
      const values: Record<string, unknown> = {
        profiles,
        settings: state.settings(),
      };
      for (const p of profiles)
        for (const k of ["favourites", "progress", "reminders", "teams"])
          values[p.id + ":" + k] = state.read(p.id + ":" + k, []);
      state.write("local-backup", { at: Date.now(), values });
      toast("Backup saved on this TV");
    }),
    action("backup-restore", "Restore local backup", () => {
      const backup = state.read<{ values: Record<string, unknown> } | null>(
        "local-backup",
        null,
      );
      if (!backup) {
        toast("No local backup exists");
        return;
      }
      confirm("Restore the saved local backup?", () => {
        for (const [key, value] of Object.entries(backup.values))
          state.write(key, value);
        state.switchProfile(state.profiles()[0].id);
        applyPreferences();
        closeOverlay();
        navigate("settings");
        toast("Local backup restored");
      });
    }),
    action("backup-close", "Close", closeOverlay),
  );
  focus.focus("backup-save");
}
function customizeSettings() {
  const b = openOverlay("Category visibility"),
    search = input("customize-search", "Find a category"),
    rows = node("div");
  b.append(search, rows, action("customize-close", "Close", closeOverlay));
  const draw = () => {
    rows.replaceChildren();
    for (const [kind, cats] of Object.entries(categories))
      for (const c of cats ?? []) {
        if (
          !search.value ||
          !c.name.toLowerCase().includes(search.value.toLowerCase())
        )
          continue;
        const id = kind + ":" + c.id,
          hidden = state.read<string[]>("hidden-categories", []);
        rows.append(
          action(
            "hide-" + kind + "-" + c.id,
            c.name + " · " + (hidden.includes(id) ? "Hidden" : "Visible"),
            () => {
              const ids = state.read<string[]>("hidden-categories", []);
              state.write(
                "hidden-categories",
                ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id],
              );
              draw();
            },
          ),
        );
        if (rows.childElementCount >= 30) return;
      }
  };
  search.addEventListener("input", draw);
  focus.focus(search.id);
}

function confirm(title: string, yes: () => void) {
  const box = openOverlay(title);
  box.append(
    action("confirm-no", "Cancel", closeOverlay),
    action("confirm-yes", "Confirm", yes),
  );
  focus.focus("confirm-no");
}
function persistPlaybackTrace() {
  if (cacheAccount)
    try {
      state.write("last-playback-trace", player.trace.slice(-180));
    } catch {
      /* Playback must survive a full local store. */
    }
}
player.onTrace = () => {
  if (traceSaveTimer) return;
  traceSaveTimer = setTimeout(() => {
    traceSaveTimer = undefined;
    persistPlaybackTrace();
  }, 500);
};
function showDiagnostics() {
  const box = openOverlay("Playback diagnostics");
  const history =
    player.trace.length > 1
      ? player.trace
      : state.read<typeof player.trace>("last-playback-trace", player.trace);
  const lastIssue = [...history]
    .reverse()
    .find((t) =>
      /error|exception|timeout|adaptive-.*http|play-rejected|unhandled-promise/i.test(
        t.event,
      ),
    );
  box.append(
    node(
      "h3",
      "diagnostic-summary",
      player.failureSummary ||
        (lastIssue
          ? `Latest issue: ${lastIssue.event}${lastIssue.code ? " · Code " + lastIssue.code : ""}`
          : "No failure code captured. Startup milestones are shown below, newest first."),
    ),
  );
  const frame = [...history]
    .reverse()
    .find((t) => t.event === "first-decoded-frame");
  box.append(
    node(
      "p",
      "muted",
      frame
        ? `Last first decoded frame: ${((frame.attemptElapsed ?? frame.elapsed ?? 0) / 1000).toFixed(1)} seconds. Guide: ${guideSummary?.channels ?? 0} channels. Sports: ${events.length} fixtures.`
        : `Guide: ${guideSummary?.channels ?? 0} channels. Sports: ${events.length} fixtures. No decoded-frame timing reported yet.`,
    ),
    node(
      "p",
      "muted",
      "Local session trace. Credentials and stream URLs are excluded.",
    ),
    node(
      "pre",
      "diagnostics",
      JSON.stringify(history.slice(-30).reverse(), null, 2),
    ),
    action("diagnostics-close", "Close", closeOverlay),
  );
  focus.focus("diagnostics-close");
}
let guideGrid: GuideGrid | undefined;
let guidePendingFocus:
  | { index: number; time: number; channel?: boolean }
  | undefined;
function renderGuide(main: HTMLElement) {
  main.className = "guide";
  if (!catalogues.live) {
    header(main, "TV Guide");
    unavailable(main, "live");
    return;
  }
  if (selected.live === undefined) selected.live = categories.live?.[0]?.id;
  const channels = filtered("live");
  guideOffset = Math.max(0, Math.min(guideOffset, channels.length - 1));
  let chosen = currentPlaying ?? channels[guideOffset];
  const top = node("section", "guide-top");
  top.id = "preview-stage";
  const logo = image(chosen?.image ?? "", "preview-logo");
  const watch = action(
    "guide-watch",
    "Watch " + (chosen?.name ?? "channel"),
    () => {
      if (chosen) void play(chosen);
    },
    "",
    "PLAY",
  );
  top.append(logo, watch);
  main.append(top);
  const info = node("section", "guide-info");
  info.id = "guide-info";
  info.append(node("h2", "", chosen?.name ?? "TV Guide"));
  main.append(info);
  const tools = node("div", "guide-toolbar");
  const changeWindow = (delta: number) => {
    guideStart += delta;
    guidePendingFocus = {
      index: guideGrid?.index ?? guideOffset,
      time: guideStart + 1,
    };
    navigate("guide", true);
  };
  tools.append(
    node("h1", "", "TV Guide"),
    action(
      "guide-category",
      categories.live?.find((c) => c.id === selected.live)?.label ??
        "All channels",
      () =>
        categoryPicker("live", () => {
          guideOffset = 0;
          guidePendingFocus = { index: 0, time: Date.now(), channel: true };
          navigate("guide");
        }),
      "tool",
      "LIST",
    ),
    action(
      "guide-now",
      "Now",
      () => {
        guideStart = Math.floor(Date.now() / 1800000) * 1800000;
        guidePendingFocus = { index: guideGrid?.index ?? 0, time: Date.now() };
        navigate("guide", true);
      },
      "tool",
      "CLOCK",
    ),
    action(
      "guide-earlier",
      "Earlier",
      () => changeWindow(-7200000),
      "tool",
      "CHEVRON_LEFT",
    ),
    action(
      "guide-later",
      "Later",
      () => changeWindow(7200000),
      "tool",
      "CHEVRON",
    ),
  );
  main.append(tools);
  const toolbar = [...tools.querySelectorAll<HTMLElement>("[data-focus]")];
  toolbar.forEach((b, i) => {
    b.dataset.left = toolbar[Math.max(0, i - 1)].id;
    b.dataset.right = toolbar[Math.min(toolbar.length - 1, i + 1)].id;
  });
  const grid = node("div", "guide-grid"),
    timeline = node("div", "guide-timeline");
  timeline.append(node("span", "guide-channel-label", "Channels"));
  for (let i = 0; i < 4; i++)
    timeline.append(node("b", "", nzTime(guideStart + i * 1800000)));
  grid.append(timeline);
  const rows = node("div", "guide-rows");
  rows.id = "guide-rows";
  rows.dataset.scroll = "";
  grid.append(rows);
  main.append(grid);
  guideGrid = new GuideGrid(rows, {
    channels,
    start: guideStart,
    read: queuedGuide,
    watch: (m) => void play(m),
    focus: (id) => focus.focus(id, false),
    leave: () => navEntry(),
    toolbar: () => focus.focus("guide-category", false),
    shift: (delta, index, time) => {
      guideStart += delta;
      guidePendingFocus = { index, time };
      navigate("guide", true);
    },
    info: (m, p) => {
      chosen = m;
      guideOffset = channels.indexOf(m);
      logo.src = m.image || "";
      watch.querySelector("span")!.textContent = "Watch " + m.name;
      info.replaceChildren(
        node(
          "p",
          "eyebrow",
          p ? `${nzTime(p.start)} – ${nzTime(p.end)} · ${m.name}` : m.name,
        ),
        node("h2", "", p?.title ?? m.name),
        node("p", "muted", p?.description ?? ""),
      );
    },
  });
  collections.set(rows.id, guideGrid.collection);
  if (currentPlaying && preview) positionVideo(true);
}

function allMedia() {
  return [
    ...Object.values(catalogues).flat(),
    ...state.favourites(),
    ...state.progress().map((p) => p.media),
    ...episodeQueue,
  ] as Media[];
}
function contextMenu(m: Media) {
  const box = openOverlay(m.name);
  box.append(
    action(
      "context-play",
      m.kind === "series" ? "Episodes" : "Watch",
      () => (m.kind === "series" ? openDetails(m) : play(m)),
      "",
      "PLAY",
    ),
    action(
      "context-save",
      state.saved(m) ? "Remove from Saved" : "Add to Saved",
      () => {
        const saved = state.toggle(m);
        closeOverlay();
        toast(saved ? "Added to Saved" : "Removed from Saved");
        if (route === "saved") navigate("saved");
      },
      "",
      "FAVORITE",
    ),
  );
  if (m.kind === "live")
    box.append(
      action(
        "context-schedule",
        "Programme guide",
        () => showSchedule(m),
        "",
        "EPG",
      ),
    );
  box.append(action("context-close", "Close", closeOverlay));
  focus.focus("context-play");
}
function stopPlayback() {
  closeWatchPanel(false);
  if (currentPlaying && currentPlaying.kind !== "live")
    safe(() =>
      state.updateProgress(currentPlaying!, player.position, player.duration),
    );
  player.stop();
  video.hidden = true;
  hud.hidden = true;
  currentPlaying = undefined;
  preview = false;
  resize();
  clearTimeout(hudTimer);
}
function positionVideo(small: boolean) {
  video.hidden = false;
  resize();
  player.note(small ? "preview" : "fullscreen");
  video.className = small ? "video-preview" : "video-full";
  if (small) {
    const stage = document.getElementById("preview-stage");
    if (stage) {
      const r = stage.getBoundingClientRect(),
        root = document.getElementById("viewport")!.getBoundingClientRect();
      const scale = root.width / 1920;
      video.style.left = `${(r.left - root.left) / scale}px`;
      video.style.top = `${(r.top - root.top) / scale}px`;
      video.style.width = `${r.width / scale}px`;
      video.style.height = `${r.height / scale}px`;
    }
  } else {
    video.style.left = "0";
    video.style.top = "0";
    video.style.width = "100vw";
    video.style.height = "100vh";
  }
}
async function play(m: Media, position?: number, sports = false) {
  savePosition();
  closeWatchPanel(false);
  watchCategory = m.category;
  watchChannelCursor = m.id;
  if (m.kind === "series") {
    await openDetails(m);
    return;
  }
  if (!overlay.hidden) {
    const origin = returnFocus;
    closeOverlay();
    if (origin) {
      const pos = routePositions.get(route);
      if (pos) pos.focus = origin;
    }
  }
  if (
    currentPlaying?.id === m.id &&
    currentPlaying.kind === m.kind &&
    preview
  ) {
    preview = false;
    video.muted = false;
    positionVideo(false);
    renderHud();
    return;
  }
  if (currentPlaying) stopPlayback();
  currentPlaying = m;
  tracksInitialised = false;
  playingSports = sports;
  preview = false;
  video.muted = false;
  lastSave = 0;
  video.style.objectFit = state.settings().aspect;
  positionVideo(false);
  renderHud();
  if (m.kind === "live") safe(() => state.write("last-channel", m));
  const progress = state.progressFor(m);
  const from =
    position ??
    (state.settings().resume && progress && !progress.complete
      ? progress.position
      : 0);
  await player.load(
    m,
    sports ? client.sportPlayback(m) : client.playback(m),
    from,
    sports ? undefined : freeviewSource(m),
  );
}
player.onState = () => {
  if (!currentPlaying) return;
  renderHud(false);
  if (player.state === "playing") {
    if (!tracksInitialised) {
      const tracks = player.tracks("subtitle");
      player.selectTrack(
        "subtitle",
        state.settings().subtitleLanguage !== "off" &&
          state.settings().subtitles &&
          tracks.length
          ? Math.max(
              0,
              tracks.findIndex((t) =>
                t.label
                  .toLowerCase()
                  .includes(state.settings().subtitleLanguage),
              ),
            )
          : -1,
      );
      const audio = player.tracks("audio"),
        preferred = audio.find((t) =>
          t.label.toLowerCase().includes(state.settings().audioLanguage),
        );
      if (preferred) player.selectTrack("audio", preferred.index);
      tracksInitialised = true;
    }
    if (!preview) {
      clearTimeout(hudTimer);
      hudTimer = setTimeout(() => {
        if (player.state === "playing" && overlay.hidden && !watchPanel)
          hud.classList.add("dimmed");
      }, 6000);
    }
  }
};
player.onProgress = (position, total) => {
  if (
    currentPlaying &&
    currentPlaying.kind !== "live" &&
    Date.now() - lastSave > 10000
  ) {
    lastSave = Date.now();
    safe(() => state.updateProgress(currentPlaying!, position, total));
  }
  const line = document.getElementById("play-progress");
  if (line)
    (line as HTMLElement).style.width = total
      ? `${(position / total) * 100}%`
      : "0%";
  const time = document.getElementById("play-time");
  if (time) time.textContent = `${duration(position)} / ${duration(total)}`;
};
player.onEnded = () => {
  if (currentPlaying?.kind === "episode" && state.settings().autoplayNext) {
    const at = episodeQueue.findIndex((e) => e.id === currentPlaying!.id);
    if (at >= 0 && episodeQueue[at + 1]) {
      void play(episodeQueue[at + 1], 0);
      return;
    }
  }
  renderHud();
};
function renderHud(moveFocus = true) {
  if (!currentPlaying) return;
  const active = (document.activeElement as HTMLElement)?.id;
  hud.hidden = preview;
  if (preview) return;
  hud.classList.remove("dimmed");
  hud.replaceChildren();
  hud.dataset.focusScope = "";
  const top = node("div", "player-top");
  top.append(node("h2", "", currentPlaying.name));
  if (currentPlaying.kind === "live") top.append(tag("LIVE"));
  hud.append(top);
  if (["loading", "buffering"].includes(player.state))
    hud.append(
      node(
        "div",
        "player-loading",
        player.state === "loading" ? "Loading…" : "Reconnecting…",
      ),
    );
  if (player.state === "error") {
    const error = node("div", "player-error");
    error.append(
      node("h2", "", "Unable to play this stream"),
      node(
        "p",
        "muted",
        player.failureSummary ||
          "This stream stopped and automatic reconnection did not succeed. Try again or choose another channel.",
      ),
      action(
        "play-retry",
        "Try again",
        () => play(currentPlaying!, player.position, playingSports),
        "primary",
        "REFRESH",
      ),
      action(
        "play-diagnostics",
        "Playback details",
        showDiagnostics,
        "",
        "INFO",
      ),
      action("play-back", "Back", () => {
        stopPlayback();
        navigate(route, true);
      }),
    );
    hud.append(error);
    if (!watchPanel) focus.focus("play-retry");
    return;
  }
  const bottom = node("div", "player-bottom");
  if (currentPlaying.kind !== "live") {
    const progress = node("div", "play-progress-track"),
      fill = node("i");
    fill.id = "play-progress";
    progress.append(fill);
    bottom.append(progress, node("p", "", ""));
    const time = node(
      "span",
      "play-time",
      `${duration(player.position)} / ${duration(player.duration)}`,
    );
    time.id = "play-time";
    bottom.append(time);
  }
  const controls = node("div", "actions");
  if (currentPlaying.kind !== "live")
    controls.append(
      action(
        "play-rewind",
        "10 seconds",
        () => player.seek(-state.settings().seekStep),
        "",
        "SEEK_BACK",
      ),
    );
  controls.append(
    action(
      "play-pause",
      player.state === "paused" ? "Play" : "Pause",
      () => player.toggle(),
      "",
      "PLAY",
    ),
  );
  if (currentPlaying.kind !== "live")
    controls.append(
      action(
        "play-forward",
        "10 seconds",
        () => player.seek(state.settings().seekStep),
        "",
        "SEEK_FORWARD",
      ),
    );
  controls.append(
    action("play-audio", "Audio", () => tracks("audio"), "", "VOLUME_HIGH"),
    action(
      "play-subtitles",
      "Subtitles",
      () => tracks("subtitle"),
      "",
      "SUBTITLE",
    ),
    action(
      "play-save",
      state.saved(currentPlaying) ? "Saved" : "Save",
      () => {
        state.toggle(currentPlaying!);
        renderHud();
      },
      "",
      "FAVORITE",
    ),
    action("play-close", "Back", backFromPlayer, "", "CHEVRON_LEFT"),
  );
  bottom.append(controls);
  hud.append(bottom);
  if (watchPanel) return;
  if (moveFocus) focus.focus("play-pause");
  else if (active?.startsWith("play-")) focus.focus(active, false);
}
function tracks(type: "audio" | "subtitle") {
  const box = openOverlay(type === "audio" ? "Audio tracks" : "Subtitles");
  const rows = player.tracks(type);
  if (type === "subtitle")
    box.append(
      action(
        "track-off",
        "Off",
        () => {
          player.selectTrack(type, -1);
          closeOverlay();
        },
        "track-row",
      ),
    );
  rows.forEach((t) =>
    box.append(
      action(
        "track-" + t.index,
        (t.selected ? "✓ " : "") + t.label,
        () => {
          player.selectTrack(type, t.index);
          closeOverlay();
        },
        "track-row",
      ),
    ),
  );
  if (!rows.length)
    box.append(
      node(
        "p",
        "muted",
        "LG has not exposed selectable " +
          (type === "audio" ? "audio tracks" : "subtitles") +
          " for this stream.",
      ),
    );
  box.append(action("tracks-close", "Close", closeOverlay));
  focus.focus(
    type === "subtitle"
      ? "track-off"
      : rows.length
        ? "track-" + rows[0].index
        : "tracks-close",
  );
}
function closeWatchPanel(restore = true) {
  const host = document.getElementById("watch-panel");
  if (!host && !watchPanel) return;
  const list = document.getElementById("watch-channels");
  if (list) watchChannelScroll = list.scrollTop;
  collections.get("watch-channels")?.destroy();
  collections.delete("watch-channels");
  host?.remove();
  watchPanel = null;
  player.note("panel-close");
  if (restore && currentPlaying && !preview) {
    hud.classList.add("dimmed");
    focus.focus(player.state === "error" ? "play-retry" : "play-pause", false);
  }
}
function watchPanelShell(title: string, kind: "channels" | "categories") {
  closeWatchPanel(false);
  watchPanel = kind;
  clearTimeout(hudTimer);
  hud.classList.add("dimmed");
  const layer = node("section", "watch-panel");
  layer.id = "watch-panel";
  layer.dataset.focusScope = "";
  layer.setAttribute("aria-label", title);
  layer.append(node("div", "watch-panel-wash"));
  const box = node("div", "watch-panel-box");
  box.append(
    node("h2", "", title),
    node(
      "p",
      "muted",
      kind === "channels" ? "◀ Categories · Back Close" : "Back Channels",
    ),
  );
  layer.append(box);
  document.getElementById("viewport")!.append(layer);
  player.note("panel-open");
  return box;
}
function openWatchChannels(categoryId = watchCategory) {
  if (!currentPlaying || currentPlaying.kind !== "live") return;
  watchCategory = categoryId;
  const title =
      categories.live?.find((c) => c.id === watchCategory)?.label ??
      (watchCategory ? "Current channel" : "All channels"),
    box = watchPanelShell(title, "channels");
  const rows = watchCategory
    ? (catalogues.live ?? []).filter((m) => m.category === watchCategory)
    : [...(catalogues.live ?? [])];
  if (!rows.length && currentPlaying.category === watchCategory)
    rows.push(currentPlaying);
  const host = node("div", "watch-channel-list");
  host.id = "watch-channels";
  host.dataset.scroll = "";
  box.append(host);
  const collection = new VirtualCollection(host, rows.length, 1, 88, (i) => {
    const m = rows[i],
      b = action(
        "watch-channel-" + m.id,
        "",
        () => {
          selected.live = watchCategory;
          if (
            m.id === currentPlaying?.id &&
            ["playing", "paused"].includes(player.state)
          ) {
            closeWatchPanel();
            return;
          }
          void play(m, undefined, m.category === "sports-backend");
        },
        "watch-channel-row" + (m.id === currentPlaying?.id ? " tuned" : ""),
      );
    b.append(image(m.image, "channel-logo"), node("div", "watch-channel-text"));
    const text = b.querySelector(".watch-channel-text")!;
    text.append(node("b", "", m.name));
    const cached = epgCache.get(m.id)?.rows,
      now = cached && nowProgramme(cached);
    if (now) text.append(node("small", "muted", now.title));
    b.addEventListener("focus", () => (watchChannelCursor = m.id));
    return b;
  });
  collections.set(host.id, collection);
  host.scrollTop = watchChannelScroll;
  collection.render();
  const index = rows.findIndex(
    (m) => m.id === (watchChannelCursor || currentPlaying?.id),
  );
  if (!collection.focus(Math.max(0, index))) {
    box.append(
      message(
        "No channels in this category",
        "Press Left to choose another category.",
      ),
    );
    const back = action("watch-empty-back", "Categories", openWatchCategories);
    box.append(back);
    focus.focus(back.id);
  }
}
function openWatchCategories() {
  if (!currentPlaying || currentPlaying.kind !== "live") return;
  const box = watchPanelShell("Categories", "categories"),
    tree = node("div", "watch-category-tree");
  box.append(tree);
  const section = categories.live?.find((c) => c.id === watchCategory)?.section;
  if (section) {
    liveExpanded.clear();
    liveExpanded.add(section);
  }
  liveCategoryPanel(
    tree,
    "watch-",
    (id) => {
      watchChannelCursor = "";
      watchChannelScroll = 0;
      openWatchChannels(id);
    },
    watchCategory,
  );
  const id = watchCategory ? "watch-category-" + watchCategory : "watch-all";
  focus.focus(id) || focus.focus("watch-all");
}
function backFromPlayer() {
  if (
    currentPlaying?.kind === "live" &&
    ["live", "guide"].includes(route) &&
    player.state !== "error"
  ) {
    preview = true;
    hud.hidden = true;
    positionVideo(true);
    const pos = routePositions.get(route);
    if (pos) focus.focus(pos.focus, false);
    return;
  }
  stopPlayback();
  navigate(route, true);
}
focus.onBack = () => {
  if (channelDigits) {
    channelDigits = "";
    clearTimeout(channelDigitTimer);
    return;
  }
  if (starting) return;
  focusEpoch++;
  if (!overlay.hidden) {
    closeOverlay();
    return;
  }
  if (watchPanel) {
    if (watchPanel === "categories") openWatchChannels();
    else closeWatchPanel();
    return;
  }
  if (currentPlaying && !preview) {
    backFromPlayer();
    return;
  }
  if (currentPlaying && preview) {
    stopPlayback();
    navigate(route, true);
    return;
  }
  if (document.activeElement?.closest("nav")) {
    const box = openOverlay("Leave Lounge TV?");
    box.append(
      action("exit-cancel", "Keep watching", closeOverlay),
      action("exit-confirm", "Exit", () => {
        const system = (window as any).webOS?.platformBack;
        if (system) system();
        else window.close();
      }),
    );
    focus.focus("exit-cancel");
    return;
  }
  if (
    route === "settings" &&
    settingScreen.back(document.getElementById("main")!)
  )
    return;
  if (route !== "home" && document.activeElement?.id.startsWith("nav-"))
    navigate("home");
  else navEntry();
};
focus.onLong = (el) => {
  if (!overlay.hidden) return false;
  if (route === "settings" && settingScreen.long(el)) return true;
  if (el.dataset.media) {
    const m = allMedia().find((m) => key(m) === el.dataset.media);
    if (m) {
      contextMenu(m);
      return true;
    }
  }
  if (el.dataset.event) {
    const e = events.find((e) => e.id === el.dataset.event);
    if (e) {
      void openEvent(e);
      return true;
    }
  }
  return false;
};
let lastRemoteMove = 0;
focus.onMove = (el, dir) => {
  lastRemoteMove = Date.now();
  clearTimeout(hoverTimer);
  if (!overlay.hidden) {
    if (el.dataset.collection === "episode-list")
      return collections.get("episode-list")?.move(el, dir) ?? false;
    return false;
  }
  if (route === "guide") {
    if (guideGrid?.move(el, dir)) return true;
    if (el.closest(".guide-toolbar") && dir === "down") {
      guideGrid?.focus(guideGrid.index);
      return true;
    }
    if (el.closest(".guide-toolbar") && dir === "up") {
      focus.focus("guide-watch", false);
      return true;
    }
    if (el.id === "guide-watch" && dir === "down") {
      focus.focus("guide-category", false);
      return true;
    }
    if (el.id === "guide-category" && dir === "left") {
      navEntry();
      return true;
    }
  }
  if (route === "settings" && settingScreen.move(el, dir)) return true;
  if (watchPanel) {
    if (dir === "left") {
      watchPanel === "channels" ? openWatchCategories() : openWatchChannels();
      return true;
    }
    if (dir === "right") {
      watchPanel === "categories" ? openWatchChannels() : closeWatchPanel();
      return true;
    }
    if (watchPanel === "channels") {
      const collection = collections.get("watch-channels");
      if (collection?.move(el, dir)) return true;
      return true;
    }
    const host = document.getElementById("watch-panel")!,
      buttons = Array.from(host.querySelectorAll<HTMLElement>("[data-focus]")),
      at = buttons.indexOf(el),
      next = buttons[at + (dir === "up" ? -1 : 1)];
    if (next) focus.focus(next.id);
    return true;
  }
  if (currentPlaying?.kind === "live" && !preview && dir === "left") {
    openWatchChannels();
    return true;
  }

  if (currentPlaying && !preview) {
    hud.classList.remove("dimmed");
    return false;
  }
  if (el.closest("nav")) {
    if (dir === "right") {
      const pos = routePositions.get(route)?.focus;
      if (pos && !pos.startsWith("nav-") && focus.focus(pos, false))
        return true;
      if (route === "guide") {
        guideGrid?.focus(guideOffset, Date.now(), true);
        return true;
      }
      if (route === "live") {
        focus.focus(
          selected.live ? "live-category-" + selected.live : "live-all",
        ) || focus.focus("live-all");
        return true;
      }
      const target = app.querySelector<HTMLElement>(
        route === "movies" || route === "series"
          ? "#browse-all"
          : "main [data-focus]",
      );
      if (target) focus.focus(target.id);
      return true;
    }
    return false;
  }
  if (el.closest(".live-category-tree")) {
    if (dir === "left") {
      navEntry();
      return true;
    }
    if (dir === "right") {
      if (
        el.classList.contains("category-heading") &&
        el.getAttribute("aria-expanded") === "false"
      ) {
        el.click();
        return true;
      }
      collections.get("channels")?.focus(0);
      return true;
    }
    if (dir === "up" || dir === "down") {
      const buttons = Array.from(
        document.querySelectorAll<HTMLElement>(
          ".live-category-tree [data-focus]",
        ),
      );
      const i = buttons.indexOf(el),
        next = buttons[i + (dir === "up" ? -1 : 1)];
      if (next) focus.focus(next.id);
      return true;
    }
  }
  const rail = el.closest<HTMLElement>(".poster-rail,.event-rail");
  if (rail) {
    const buttons = [...rail.querySelectorAll<HTMLElement>("[data-focus]")],
      index = buttons.indexOf(el);
    if (dir === "left" || dir === "right") {
      const next = buttons[index + (dir === "right" ? 1 : -1)];
      if (next) focus.focus(next.id);
      else if (dir === "left") navEntry();
      return true;
    }
    const sections = [
        ...document.querySelectorAll<HTMLElement>("#home-scroll .home-rail"),
      ],
      section = el.closest<HTMLElement>(".home-rail")!,
      at = sections.indexOf(section),
      next = sections[at + (dir === "down" ? 1 : -1)];
    if (next) {
      const targets = [...next.querySelectorAll<HTMLElement>("[data-focus]")];
      if (targets.length)
        focus.focus(
          targets[Math.min(Math.max(0, index), targets.length - 1)].id,
        );
    } else if (dir === "up") focus.focus("hero-view");
    return true;
  }
  const collection =
    el.dataset.collection && collections.get(el.dataset.collection);
  if (collection) {
    if (collection.move(el, dir)) return true;
    if (dir === "left") {
      if (route === "live") {
        const id = selected.live
          ? "live-category-" + selected.live
          : "live-all";
        focus.focus(id) || focus.focus("live-all");
      } else if (route === "movies" || route === "series")
        categoryPicker(route === "movies" ? "movie" : "series");
      else navEntry();
      return true;
    }
    if (
      (dir === "up" || dir === "down") &&
      el.dataset.collection === "channels"
    ) {
      if (dir === "up") focus.focus("live-search", false);
      return true;
    }
    if (
      el.dataset.collection === "vod-grid" &&
      (dir === "up" || dir === "down" || dir === "right")
    ) {
      if (dir === "up") focus.focus("browse-all", false);
      return true;
    }
    if (
      el.dataset.collection === "sports-grid" &&
      (dir === "up" || dir === "down" || dir === "right")
    ) {
      if (dir === "up") focus.focus("sport-all", false);
      return true;
    }
    if (dir === "right" && route === "live" && browseItem) {
      void showSchedule(browseItem);
      return true;
    }
  }
  if (el.closest("#major-hero")) {
    const pages = [
      ...document.querySelectorAll<HTMLElement>(".hero-pager [data-focus]"),
    ];
    const actions = [
      ...document.querySelectorAll<HTMLElement>(
        "#major-hero .actions [data-focus]",
      ),
    ];
    const page = pages.findIndex((p) => p.id === el.id),
      action = actions.findIndex((b) => b.id === el.id);
    if (dir === "left" || dir === "right") {
      if (page >= 0) {
        const next = pages[page + (dir === "right" ? 1 : -1)];
        if (next) focus.focus(next.id, false);
        else if (dir === "left") navEntry();
        return true;
      }
      if (action >= 0) {
        const next = actions[action + (dir === "right" ? 1 : -1)];
        if (next) focus.focus(next.id, false);
        else if (dir === "left") navEntry();
        else if (pages.length)
          focus.focus(pages[heroIndex % pages.length].id, false);
        return true;
      }
    }
    if (dir === "up") {
      if (page >= 0 && actions[0]) focus.focus(actions[0].id, false);
      else focus.focus("active-profile", false);
      return true;
    }
    if (dir === "down") {
      if (action >= 0 && pages.length)
        focus.focus(pages[heroIndex % pages.length].id, false);
      else {
        const first = document.querySelector<HTMLElement>(
          ".home-rail [data-focus]",
        );
        if (first) focus.focus(first.id);
      }
      return true;
    }
  }
  return false;
};
let channelDigits = "",
  channelDigitTimer: ReturnType<typeof setTimeout> | undefined;
function tuneDigits() {
  const index = Number(channelDigits) - 1;
  channelDigits = "";
  clearTimeout(channelDigitTimer);
  const m = filtered("live")[index];
  if (m) void play(m);
  else toast("Channel number unavailable");
}
focus.onMedia = (code) => {
  if (
    overlay.hidden &&
    state.settings().channelNumbers &&
    (route === "live" || currentPlaying?.kind === "live") &&
    document.activeElement?.tagName !== "INPUT"
  ) {
    const digit =
      code >= 48 && code <= 57
        ? code - 48
        : code >= 96 && code <= 105
          ? code - 96
          : -1;
    if (digit >= 0) {
      channelDigits = (channelDigits + digit).slice(-4);
      toast("Channel " + channelDigits);
      clearTimeout(channelDigitTimer);
      channelDigitTimer = setTimeout(tuneDigits, 1000);
      return true;
    }
    if (code === 13 && channelDigits) {
      tuneDigits();
      return true;
    }
  }
  if (!currentPlaying) return false;
  if ([415, 19, 10252].includes(code)) {
    safe(() =>
      code === 415
        ? player.resumeByUser()
        : code === 19
          ? player.pauseByUser()
          : player.toggle(),
    );
    renderHud();
    return true;
  }
  if (code === 413) {
    stopPlayback();
    navigate(route, true);
    return true;
  }
  if (code === 417 || code === 412) {
    player.seek(
      code === 417 ? state.settings().seekStep : -state.settings().seekStep,
    );
    renderHud();
    return true;
  }
  if ([427, 428].includes(code) && currentPlaying.kind === "live") {
    const rows = filtered("live");
    const index = rows.findIndex((m) => m.id === currentPlaying!.id),
      m = rows[(index + (code === 427 ? 1 : -1) + rows.length) % rows.length];
    if (m) void play(m);
    return true;
  }
  return false;
};
document.addEventListener("visibilitychange", () => {
  player.note(document.hidden ? "app-hidden" : "app-visible");
  if (document.hidden && currentPlaying) {
    safe(() =>
      state.updateProgress(currentPlaying!, player.position, player.duration),
    );
    stopPlayback();
    persistPlaybackTrace();
  }
});
window.addEventListener("pagehide", () => {
  cancelPhoneLogin?.();
  player.note("pagehide");
  persistPlaybackTrace();
  if (currentPlaying)
    safe(() =>
      state.updateProgress(currentPlaying!, player.position, player.duration),
    );
  player.stop();
});
setInterval(() => {
  if (
    !starting &&
    !document.hidden &&
    route === "guide" &&
    overlay.hidden &&
    (!currentPlaying || preview)
  )
    guideGrid?.refresh();
}, 60000);
// Update only visible guide rows. No shell rebuild, focus change or player reload.
setInterval(() => {
  if (
    starting ||
    document.hidden ||
    route !== "live" ||
    !overlay.hidden ||
    (currentPlaying && !preview)
  )
    return;
  const token = view;
  for (const row of document.querySelectorAll<HTMLElement>(
    "#channels .channel-row",
  )) {
    const id = row.id.replace(/^channel-/, "");
    void queuedGuide(id).then((rows) => {
      if (token === view && row.isConnected) setChannelGuide(row, rows);
    });
  }
}, 60000);
// Reminders are local and shown while the application is running. No unsupported background wake claim.
setInterval(() => {
  if (!client || !state.settings().remindersEnabled) return;
  for (const e of state.reminders())
    if (
      e.startUtcMs - Date.now() < state.settings().reminderLead * 60000 &&
      e.startUtcMs > Date.now() &&
      !state.read<string[]>("reminders-shown", []).includes(e.id)
    ) {
      toast(`${e.title} starts at ${nzTime(e.startUtcMs)}`);
      state.write("reminders-shown", [
        ...state.read<string[]>("reminders-shown", []),
        e.id,
      ]);
    }
}, 30000);
async function boot() {
  video.hidden = true;
  const splash = document.getElementById("boot-splash");
  if (splash) {
    const logo = splash.querySelector<HTMLImageElement>("img");
    if (logo?.decode) await logo.decode().catch(() => {});
    await new Promise<void>((resolve) => setTimeout(resolve, 6000));
  }
  const auth = state.credentials();
  if (!auth) {
    login();
    return;
  }
  await startSession(auth);
}
void boot();
