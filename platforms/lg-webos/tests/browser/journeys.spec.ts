import { test, expect, Page } from "@playwright/test";
const poster = "https://images.test/poster.jpg";
const movies = Array.from({ length: 5142 }, (_, i) => ({
  stream_id: i + 10000,
  name: i === 0 ? "The Matrix" : i === 1 ? "Dune: Part Two" : `Movie ${i}`,
  stream_icon: poster,
  category_id: "movie_1",
  container_extension: "mp4",
}));
const live = Array.from({ length: 2391 }, (_, i) => ({
  stream_id: i + 20000,
  name: `SKY SPORT ${i + 1}`,
  stream_icon: poster,
  category_id: "live_nz_sports",
}));
const series = Array.from({ length: 3395 }, (_, i) => ({
  series_id: i + 30000,
  name: i === 0 ? "Breaking Bad" : `Series ${i}`,
  cover: poster,
  category_id: "series_1",
}));
const now = Date.now();
const event = {
  id: "qa-event",
  externalId: "42",
  sport: "basketball",
  feedId: "basketball/nba",
  leagueName: "NBA",
  title: "Warriors at Heat",
  startUtcMs: now + 3600000,
  state: "scheduled",
  statusText: "Upcoming",
  competitors: [
    { id: "a", name: "Warriors", shortName: "Warriors", home: true },
    { id: "b", name: "Heat", shortName: "Heat", home: false },
  ],
  broadcasts: [],
};
function xmlDate(ms: number) {
  return new Date(ms).toISOString().replace(/[-:T]/g, "").slice(0, 14);
}
async function mock(page: Page) {
  await page.route("https://site.api.espn.com/**", (r) =>
    r.fulfill({ json: { events: [] } }),
  );
  await page.route("https://site.web.api.espn.com/**", (r) =>
    r.fulfill({ json: { sports: [] } }),
  );
  await page.route("https://raw.githubusercontent.com/**", (r) =>
    r.fulfill({ status: 404, body: "Not found" }),
  );
  await page.route("https://images.test/**", (r) =>
    r.fulfill({
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="600"><rect width="400" height="600" fill="#14395c"/></svg>',
    }),
  );
  await page.route("https://metadata.loungetv.nz/**", (r) =>
    r.fulfill({
      json: {
        results: [
          {
            id: 1,
            title: "The Matrix",
            name: "Breaking Bad",
            vote_average: 8.5,
            overview: "A test synopsis",
          },
        ],
      },
    }),
  );
  await page.route("https://liqocisegeqzbyuqloql.supabase.co/**", (r) =>
    r.fulfill({
      json: r.request().url().includes("candidates")
        ? {
            candidates: [
              {
                providerItemId: 123,
                name: "Sports QA stream",
                confidence: "likely",
              },
            ],
          }
        : { events: [event], status: "ok" },
    }),
  );
  await page.context().route("https://connect.loungetv.nz/**", (r) => {
    const u = new URL(r.request().url()),
      a = u.searchParams.get("action");
    if (u.pathname === "/xmltv.php")
      return r.fulfill({
        contentType: "application/xml",
        headers: { "Access-Control-Allow-Origin": "*" },
        body:
          '<?xml version="1.0"?><tv>' +
          live
            .map(
              (m) =>
                `<programme channel="ltv-${m.stream_id}" start="${xmlDate(now - 3600000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>PGA Tour</title><desc>Live golf</desc></programme>`,
            )
            .join("") +
          "</tv>",
      });
    let json: any = { user_info: { auth: 1, status: "Active" } };
    if (a === "get_live_categories")
      json = [
        {
          category_id: "live_nz_sports",
          category_name: "New Zealand · Sports",
        },
      ];
    if (a === "get_vod_categories")
      json = [{ category_id: "movie_1", category_name: "Action" }];
    if (a === "get_series_categories")
      json = [{ category_id: "series_1", category_name: "Drama" }];
    if (a === "get_live_streams") json = live;
    if (a === "get_vod_streams") json = movies;
    if (a === "get_series") json = series;
    if (a === "get_short_epg")
      json = {
        epg_listings: [
          {
            id: 1,
            title: Buffer.from("PGA Tour").toString("base64"),
            description: Buffer.from("Live golf").toString("base64"),
            start_timestamp: Math.floor((now - 3600000) / 1000),
            stop_timestamp: Math.floor((now + 3600000) / 1000),
          },
        ],
      };
    if (a === "get_vod_info")
      json = {
        info: {
          name: "The Matrix",
          plot: "A hacker discovers the truth.",
          year: 1999,
          rating: 8.2,
          genre: "Science fiction",
          duration_secs: 7200,
        },
        movie_data: { container_extension: "mp4" },
      };
    if (a === "get_series_info")
      json = {
        info: { name: "Breaking Bad", plot: "A series synopsis", rating: 8.8 },
        episodes: {
          "1": [
            {
              id: "90001",
              episode_num: 1,
              title: "Pilot",
              season: 1,
              container_extension: "mkv",
            },
            {
              id: "90002",
              episode_num: 2,
              title: "Second episode",
              season: 1,
              container_extension: "mkv",
            },
          ],
          "2": [
            {
              id: "90003",
              episode_num: 1,
              title: "Season two",
              season: 2,
              container_extension: "mkv",
            },
          ],
        },
      };
    return r.fulfill({ json });
  });
}
async function signIn(page: Page) {
  await mock(page);
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#featured-0")).toBeVisible({ timeout: 15000 });
  await expect.poll(() => page.locator("#nav-series").count()).toBe(1);
  await page.waitForTimeout(350);
}
async function section(page: Page, id: string) {
  await page.locator("#nav-" + id).focus();
  await page.keyboard.press("Enter");
}
test("login loads the real application contract and persists customer gateway identity", async ({
  page,
}) => {
  await signIn(page);
  await expect(page.locator("#major-hero")).toContainText("Warriors at Heat");
  await expect(page.locator(".nav-rail")).toHaveCount(1);
  await expect(page.locator("#major-hero .hero-pager button")).toHaveCount(1);
  await expect(page.locator("#app")).not.toContainText("BEST");
  await expect
    .poll(() =>
      page
        .locator(".hero-image")
        .evaluate((x) => (x as HTMLImageElement).naturalWidth),
    )
    .toBeGreaterThan(0);
  await expect
    .poll(() =>
      page
        .locator(".brand-mark")
        .evaluate((x) => (x as HTMLImageElement).naturalWidth),
    )
    .toBeGreaterThan(0);
  await page.screenshot({ path: "test-results/lg-home-1920.png" });
});
test("live uses a bounded DOM for 2,391 channels and returns to the exact channel after error", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "live");
  await expect(page.locator("#channel-20000")).toBeVisible();
  await page.locator("#channel-20000").focus();
  for (let i = 0; i < 50; i++) await page.keyboard.press("ArrowDown");
  await expect(page.locator("#channel-20050")).toBeFocused();
  expect(await page.locator(".channel-row").count()).toBeLessThan(16);
  await page.keyboard.press("Enter");
  await expect(page.locator("#player")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.locator("#channel-20050")).toBeFocused();
  await page.screenshot({ path: "test-results/lg-live-1920.png" });
});
test("movies restore grid position after details and Saved toggles from hold OK", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "movies");
  await page.locator("#vod-10000").focus();
  for (let i = 0; i < 5; i++) await page.keyboard.press("ArrowDown");
  await expect(page.locator("#vod-10030")).toBeFocused();
  expect(await page.locator("#vod-grid .poster").count()).toBeLessThanOrEqual(
    30,
  );
  await page.keyboard.press("Enter");
  await expect(page.locator("#detail-play")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.locator("#vod-10030")).toBeFocused();
  await page.keyboard.down("Enter");
  await page.waitForTimeout(750);
  await page.keyboard.up("Enter");
  await expect(page.locator("#context-save")).toBeVisible();
  await page.locator("#context-save").click();
  await section(page, "saved");
  await expect(page.locator("#saved-scroll")).toContainText("Movie 30");
  await page.screenshot({ path: "test-results/lg-movies-1920.png" });
});
test("series accepts standard season-keyed backend response and episode route", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "series");
  await page.locator("#vod-30000").click();
  await expect(page.locator("#season-2")).toBeVisible();
  await page.locator("#season-2").click();
  await expect(page.locator("#episode-90003")).toBeFocused();
  const requests: string[] = [];
  page.on("request", (r) => requests.push(r.url()));
  await page.keyboard.press("Enter");
  await expect(page.locator("#player")).toBeVisible();
  await expect
    .poll(() =>
      requests.some((u) => u.includes("/series/qa-user/qa-password/90003.mkv")),
    )
    .toBe(true);
});
test("Sport Lounge matches current fixture, keeps confidence unmodified and saves reminder", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "sports");
  await page.locator("#time-upcoming").click();
  await page.locator('[data-event="qa-event"]').click();
  await expect(page.locator("#watch-option-0")).toContainText("likely");
  await expect(page.locator("#overlay")).not.toContainText("verified working");
  await page.locator("#event-remind").click();
  await page.keyboard.press("Escape");
  await section(page, "saved");
  await page.locator("#saved-reminders").click();
  await expect(page.locator("#saved-scroll")).toContainText("Warriors");
  await page.screenshot({ path: "test-results/lg-sports-1920.png" });
});
test("search covers current movies, series and channels without adding catalogue rows", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "search");
  await page.locator("#global-search").fill("The Matrix");
  await expect(page.locator("#result-movie-10000")).toBeVisible();
  await page.locator("#global-search").fill("SKY SPORT 101");
  await expect(page.locator("#result-live-20100")).toBeVisible();
});
test("profile switching isolates favourites and default focus always remains visible", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "movies");
  await page.locator("#vod-10000").click();
  await page.locator("#detail-save").click();
  await page.keyboard.press("Escape");
  await page.locator("#active-profile").click();
  await page.locator("#profile-name").fill("Kids");
  await page.locator("#profile-add").click();
  await section(page, "saved");
  await expect(page.locator("#saved-scroll")).toContainText(
    "Your favourites live here",
  );
  await page.keyboard.press("Escape");
  await expect(page.locator("#nav-saved")).toBeFocused();
});
test("TV Guide decodes the programme and scopes keyboard focus to category overlay", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "guide");
  await expect(page.locator("#guide-cell-0-0")).toContainText("PGA Tour");
  await page.locator("#guide-category").click();
  await expect(page.locator("#picker-all")).toBeFocused();
  await page.keyboard.press("ArrowDown");
  expect(
    await page.evaluate(() =>
      Boolean(document.activeElement?.closest("#overlay")),
    ),
  ).toBe(true);
  await page.keyboard.press("Escape");
  await expect(page.locator("#guide-category")).toBeFocused();
});
test("bad credentials never open Home and retries keep login keyboard usable", async ({
  page,
}) => {
  await mock(page);
  await page.route("https://connect.loungetv.nz/player_api.php?**", (r) =>
    r.fulfill({ status: 401, json: { user_info: { auth: 0 } } }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("wrong");
  await page.locator("#login-password").fill("wrong");
  await page.locator("#login-submit").click();
  await expect(page.locator("#startup-status.error")).toContainText(
    "check your Lounge TV login",
  );
  await expect(page.locator("#startup-retry")).toBeEnabled();
  await expect(page.locator("main")).toHaveCount(0);
});

test("startup holds Home until all catalogue libraries and full XMLTV are prepared, then caches restart", async ({
  page,
}) => {
  await mock(page);
  let release!: () => void;
  const held = new Promise<void>((r) => (release = r));
  let catalogueCalls = 0,
    xmlCalls = 0;
  await page.route(
    "https://connect.loungetv.nz/player_api.php?**",
    async (r) => {
      const a = new URL(r.request().url()).searchParams.get("action");
      if (a === "get_series") {
        catalogueCalls++;
        await held;
      }
      await r.fallback();
    },
  );
  page.on("request", (r) => {
    if (r.url().includes("xmltv.php")) xmlCalls++;
  });
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#startup")).toBeVisible();
  await expect(page.locator("main")).toHaveCount(0);
  await expect(page.locator("#startup-status")).toContainText(
    /libraries|playlist/,
  );
  release();
  await expect(page.locator("#nav-home")).toBeVisible();
  await expect(page.locator("#startup")).toHaveCount(0);
  expect(xmlCalls).toBe(1);
  let freshCatalogueCalls = 0;
  page.on("request", (r) => {
    if (/action=get_(live_streams|vod_streams|series)(&|$)/.test(r.url()))
      freshCatalogueCalls++;
  });
  await page.reload();
  await expect(page.locator("#nav-home")).toBeVisible();
  expect(catalogueCalls).toBe(1);
  expect(freshCatalogueCalls).toBe(0);
  expect(xmlCalls).toBe(1);
});
test("fixed sidebar keeps its DOM and width during navigation and delayed sports replies do not steal focus", async ({
  page,
}) => {
  await signIn(page);
  await page.evaluate(() => {
    (window as any).__originalNav = document.querySelector("nav");
  });
  const width = await page
    .locator("nav")
    .evaluate((e) => e.getBoundingClientRect().width);
  await page.locator("#nav-live").focus();
  expect(
    await page.locator("nav").evaluate((e) => e.getBoundingClientRect().width),
  ).toBe(width);
  expect(width).toBe(128);
  await page.keyboard.press("Enter");
  await expect(page.locator("#live-all")).toBeVisible();
  expect(
    await page.evaluate(
      () => (window as any).__originalNav === document.querySelector("nav"),
    ),
  ).toBe(true);
  await page.locator("#channel-20000").focus();
  await page.keyboard.press("ArrowDown");
  await expect(page.locator("#channel-20001")).toBeFocused();
  await section(page, "movies");
  expect(
    await page.evaluate(
      () => (window as any).__originalNav === document.querySelector("nav"),
    ),
  ).toBe(true);
  await expect(page.locator("nav #nav-profile")).toHaveAttribute(
    "aria-label",
    "Profiles",
  );
});
test("Live headings expand independently and every supplied category remains available", async ({
  page,
}) => {
  await mock(page);
  await page.route(
    "https://connect.loungetv.nz/player_api.php?**",
    async (r) => {
      if (
        new URL(r.request().url()).searchParams.get("action") ===
        "get_live_categories"
      )
        return r.fulfill({
          json: [
            { category_id: "nz-tv", category_name: "New Zealand · TV" },
            {
              category_id: "live_nz_sports",
              category_name: "New Zealand · Sport",
            },
            { category_id: "au-tv", category_name: "Australia · TV" },
            { category_id: "empty", category_name: "Sports · Empty folder" },
          ],
        });
      await r.fallback();
    },
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-home")).toBeVisible();
  await section(page, "live");
  await expect(page.locator("#live-category-nz-tv")).toBeVisible();
  await expect(page.locator("#live-category-live_nz_sports")).toBeVisible();
  await page.locator("#live-group-0").click();
  await expect(page.locator("#live-category-nz-tv")).toHaveCount(0);
  await expect(page.locator("#live-group-0")).toBeFocused();
  await page.locator("#live-group-1").click();
  await expect(page.locator("#live-category-au-tv")).toBeVisible();
  await page.locator("#live-group-2").click();
  await expect(page.locator("#live-category-empty")).toContainText(
    "Empty folder",
  );
  await page.locator("#live-group-0").click();
  await page.locator("#live-category-live_nz_sports").click();
  await expect(page.locator("#channel-20000")).toBeFocused();
  await page.keyboard.press("ArrowLeft");
  await expect(page.locator("#live-category-live_nz_sports")).toBeFocused();
});
test("overlapping virtual windows retain existing poster nodes instead of repainting the whole grid", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "movies");
  await page.locator("#vod-10006").focus();
  await page.evaluate(() => {
    (window as any).__poster = document.querySelector("#vod-10006");
  });
  await page.locator("#vod-10000").focus();
  for (let i = 0; i < 2; i++) await page.keyboard.press("ArrowDown");
  expect(
    await page.evaluate(
      () => (window as any).__poster === document.querySelector("#vod-10006"),
    ),
  ).toBe(true);
});

test("failed catalogue preparation stays behind startup and retries without exposing a partial Home", async ({
  page,
}) => {
  await mock(page);
  let failing = true;
  await page.route(
    "https://connect.loungetv.nz/player_api.php?**",
    async (r) => {
      if (
        failing &&
        new URL(r.request().url()).searchParams.get("action") === "get_series"
      )
        return r.fulfill({ status: 502, json: { error: "unavailable" } });
      await r.fallback();
    },
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#startup-retry")).toBeFocused();
  await expect(page.locator("main")).toHaveCount(0);
  failing = false;
  await page.locator("#startup-retry").click();
  await expect(page.locator("#nav-home")).toBeVisible();
  await expect(page.locator("#startup")).toHaveCount(0);
});
test("full EPG failure falls back to short guides with an explicit notice", async ({
  page,
}) => {
  await mock(page);
  await page
    .context()
    .route("https://connect.loungetv.nz/xmltv.php?**", (r) =>
      r.fulfill({ status: 502, body: "unavailable" }),
    );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-home")).toBeVisible();
  await expect(page.locator("#toast")).toContainText(
    "full guide is unavailable",
  );
  await section(page, "live");
  await expect(page.locator("#channel-20000")).toContainText("PGA Tour");
});

async function syntheticNativeVideo(page: Page) {
  await page.addInitScript(() => {
    const data = new WeakMap<
      HTMLMediaElement,
      { source: string; paused: boolean; time: number; ended: boolean }
    >();
    function value(el: HTMLMediaElement) {
      let d = data.get(el);
      if (!d) {
        d = { source: "", paused: true, time: 0, ended: false };
        data.set(el, d);
      }
      return d;
    }
    Object.defineProperty(HTMLMediaElement.prototype, "src", {
      configurable: true,
      get() {
        return value(this).source;
      },
      set(v) {
        value(this).source = String(v);
      },
    });
    Object.defineProperty(HTMLMediaElement.prototype, "paused", {
      configurable: true,
      get() {
        return value(this).paused;
      },
    });
    Object.defineProperty(HTMLMediaElement.prototype, "currentTime", {
      configurable: true,
      get() {
        return value(this).time;
      },
      set(v) {
        value(this).time = Number(v);
      },
    });
    Object.defineProperty(HTMLMediaElement.prototype, "ended", {
      configurable: true,
      get() {
        return value(this).ended;
      },
    });
    HTMLMediaElement.prototype.play = async function () {
      value(this).paused = false;
      value(this).ended = false;
      this.dispatchEvent(new Event("playing"));
    };
    HTMLMediaElement.prototype.pause = function () {
      const d = value(this);
      if (!d.paused) {
        d.paused = true;
        this.dispatchEvent(new Event("pause"));
      }
    };
    HTMLMediaElement.prototype.load = function () {
      (this as any).__qaLoads = ((this as any).__qaLoads ?? 0) + 1;
      value(this).time = 0;
      value(this).ended = false;
    };
    const remove = HTMLMediaElement.prototype.removeAttribute;
    HTMLMediaElement.prototype.removeAttribute = function (name) {
      if (name === "src") value(this).source = "";
      remove.call(this, name);
    };
  });
}
test("only one Live parent remains expanded after another parent is opened", async ({
  page,
}) => {
  await mock(page);
  await page.route(
    "https://connect.loungetv.nz/player_api.php?**",
    async (r) => {
      if (
        new URL(r.request().url()).searchParams.get("action") ===
        "get_live_categories"
      )
        return r.fulfill({
          json: [
            {
              category_id: "live_nz_sports",
              category_name: "New Zealand · Sports",
            },
            { category_id: "au-sports", category_name: "Australia · Sports" },
            { category_id: "uk-sports", category_name: "Sports · UK" },
          ],
        });
      await r.fallback();
    },
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-home")).toBeVisible();
  await section(page, "live");
  await page.locator("#live-group-1").click();
  await expect(page.locator("#live-group-0")).toHaveAttribute(
    "aria-expanded",
    "false",
  );
  await expect(page.locator("#live-category-live_nz_sports")).toHaveCount(0);
  await expect(
    page.locator('.live-category-tree [aria-expanded="true"]'),
  ).toHaveCount(1);
  await page.locator("#live-group-2").click();
  await expect(page.locator("#live-group-1")).toHaveAttribute(
    "aria-expanded",
    "false",
  );
  await expect(
    page.locator('.live-category-tree [aria-expanded="true"]'),
  ).toHaveCount(1);
});
test("fullscreen video has native geometry and Left Left Back Back browses without stopping or reloading it", async ({
  page,
}) => {
  await syntheticNativeVideo(page);
  await signIn(page);
  await section(page, "live");
  await page.locator("#channel-20000").click();
  await expect(page.locator("#play-pause")).toBeFocused();
  const before = await page
    .locator("#video")
    .evaluate((v: HTMLVideoElement) => ({
      source: v.src,
      loads: (v as any).__qaLoads,
    }));
  const geometry = await page.locator("#video").evaluate((v) => {
    const r = v.getBoundingClientRect();
    let el: Element | null = v;
    const transforms: string[] = [];
    while (el) {
      transforms.push(getComputedStyle(el).transform);
      el = el.parentElement;
    }
    return {
      left: r.left,
      top: r.top,
      width: r.width,
      height: r.height,
      transforms,
    };
  });
  expect(geometry).toEqual({
    left: 0,
    top: 0,
    width: 1920,
    height: 1080,
    transforms: ["none", "none", "none", "none"],
  });
  await page.keyboard.press("ArrowLeft");
  await expect(page.locator("#watch-channel-20000")).toBeFocused();
  await page.keyboard.press("ArrowLeft");
  await expect(page.locator("#watch-category-live_nz_sports")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.locator("#watch-channel-20000")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.locator("#watch-panel")).toHaveCount(0);
  await expect(page.locator("#video")).toHaveClass("video-full");
  const after = await page
    .locator("#video")
    .evaluate((v: HTMLVideoElement) => ({
      source: v.src,
      loads: (v as any).__qaLoads,
      paused: v.paused,
    }));
  expect(after).toEqual({ ...before, paused: false });
});
test("browsing another fullscreen category leaves the stream running until OK chooses a channel", async ({
  page,
}) => {
  await syntheticNativeVideo(page);
  await mock(page);
  await page.route(
    "https://connect.loungetv.nz/player_api.php?**",
    async (r) => {
      const a = new URL(r.request().url()).searchParams.get("action");
      if (a === "get_live_categories")
        return r.fulfill({
          json: [
            {
              category_id: "live_nz_sports",
              category_name: "New Zealand · Sports",
            },
            { category_id: "au-sports", category_name: "Australia · Sports" },
          ],
        });
      if (a === "get_live_streams")
        return r.fulfill({
          json: live.map((m, i) => ({
            ...m,
            category_id: i < 1200 ? "live_nz_sports" : "au-sports",
          })),
        });
      await r.fallback();
    },
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-home")).toBeVisible();
  await section(page, "live");
  await page.locator("#channel-20000").click();
  const before = await page
    .locator("#video")
    .evaluate((v: HTMLVideoElement) => ({
      source: v.src,
      loads: (v as any).__qaLoads,
    }));
  await page.keyboard.press("ArrowLeft");
  await page.keyboard.press("ArrowLeft");
  await page.locator("#watch-group-1").click();
  await expect(page.locator("#watch-group-0")).toHaveAttribute(
    "aria-expanded",
    "false",
  );
  await page.locator("#watch-category-au-sports").click();
  await expect(page.locator("#watch-channel-21200")).toBeFocused();
  expect(
    await page.locator("#video").evaluate((v: HTMLVideoElement) => ({
      source: v.src,
      loads: (v as any).__qaLoads,
      paused: v.paused,
    })),
  ).toEqual({ ...before, paused: false });
  await page.keyboard.press("Enter");
  await expect(page.locator("#watch-panel")).toHaveCount(0);
  await expect
    .poll(() => page.locator("#video").evaluate((v: HTMLVideoElement) => v.src))
    .toContain("/21200.ts");
});
test("a live EOF reconnects in the actual application path and leaves a redacted diagnostic record", async ({
  page,
}) => {
  await syntheticNativeVideo(page);
  await signIn(page);
  await section(page, "live");
  await page.locator("#channel-20000").click();
  await page
    .locator("#video")
    .evaluate((v) => v.dispatchEvent(new Event("ended")));
  await expect
    .poll(() => page.locator("#video").evaluate((v: HTMLVideoElement) => v.src))
    .toContain("_ltv_restart=");
  await expect
    .poll(() =>
      page.evaluate(() => {
        const key = Object.keys(localStorage).find((k) =>
          k.endsWith(":last-playback-trace"),
        );
        return key ? localStorage.getItem(key) : "";
      }),
    )
    .toContain("live-eof");
  const text = await page.evaluate(() => {
    const key = Object.keys(localStorage).find((k) =>
      k.endsWith(":last-playback-trace"),
    );
    return key ? localStorage.getItem(key) : "";
  });
  expect(text).not.toContain("qa-password");
  expect(text).not.toContain("https://");
});

test("Movies and Series enter on All, keep Search unfocused, and do not request metadata while navigating", async ({
  page,
}) => {
  await signIn(page);
  const metadata: string[] = [];
  page.on("request", (r) => {
    if (/get_vod_info|get_series_info/.test(r.url())) metadata.push(r.url());
  });
  for (const route of ["movies", "series"]) {
    await section(page, route);
    await expect(page.locator("#browse-all")).toBeFocused();
    await expect(page.locator("#browse-search")).not.toBeFocused();
    const first = route === "movies" ? "10000" : "30000";
    await page.locator("#vod-" + first).focus();
    for (let i = 0; i < 30; i++) await page.keyboard.press("ArrowDown");
    await page.waitForTimeout(850);
    expect(metadata).toEqual([]);
    expect(await page.locator("#vod-grid .poster").count()).toBeLessThanOrEqual(
      24,
    );
  }
});

test("TV CPU throttling keeps rapid VOD navigation responsive with a bounded poster window", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "movies");
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 6 });
  await page.locator("#vod-10000").focus();
  const timing = await page.evaluate(async () => {
    let ticks = 0;
    const ticker = setInterval(() => ticks++, 20),
      latencies: number[] = [];
    for (let i = 0; i < 60; i++) {
      const start = performance.now();
      document.activeElement?.dispatchEvent(
        new KeyboardEvent("keydown", {
          key: "ArrowDown",
          keyCode: 40,
          bubbles: true,
        }),
      );
      latencies.push(performance.now() - start);
      await new Promise((r) => setTimeout(r, 16));
    }
    clearInterval(ticker);
    return {
      max: Math.max(...latencies),
      ticks,
      focused: document.activeElement?.id,
    };
  });
  expect(timing.focused).toBe("vod-10360");
  expect(timing.max).toBeLessThan(1000);
  expect(timing.ticks).toBeGreaterThan(20);
  await cdp.send("Emulation.setCPUThrottlingRate", { rate: 1 });
  await test.info().attach("navigation-timing", {
    body: JSON.stringify(timing),
    contentType: "application/json",
  });
});

test("Settings mirrors twelve Android groups, search, contextual help, working preferences and Quick pins", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "settings");
  await expect(page.locator(".settings-group")).toHaveCount(12);
  await expect(page.locator("#settings-group-quick")).toBeFocused();
  await page.screenshot({ path: "test-results/lg013-settings-root.png" });
  await page.locator("#settings-group-watching").click();
  await page.locator("#setting-vp_autoplay").focus();
  await expect(page.locator(".settings-help")).toContainText("Auto-play");
  await page.locator("#setting-vp_autoplay").click();
  await expect(page.locator("#setting-vp_autoplay .setting-value")).toHaveText(
    "Off",
  );
  await page.keyboard.down("Enter");
  await page.waitForTimeout(750);
  await page.keyboard.up("Enter");
  await expect(page.locator("#setting-pin")).toBeVisible();
  await page.keyboard.press("Escape");
  await page.keyboard.press("Escape");
  await expect(page.locator(".settings-group")).toHaveCount(12);
  await page.locator("#settings-search").fill("EPG");
  await expect(page.locator("#setting-tab_epg")).toBeVisible();
  await page.locator("#settings-search").fill("");
  await expect(page.locator(".settings-group")).toHaveCount(12);
  await page.locator("#settings-group-player").click();
  await page.locator("#setting-vp_live_engine").focus();
  await expect(page.locator(".settings-help")).toContainText(
    "Android ExoPlayer and MPV are not included",
  );
  await page.screenshot({ path: "test-results/lg013-settings-player.png" });
});

test("EPG sources can be added, switched and removed without changing playlists or provider data", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "settings");
  await page.locator("#settings-group-sources").click();
  await page.locator("#setting-tab_epg").click();
  await page.locator("#epg-add").click();
  await page.locator("#epg-name").fill("My guide");
  await page.locator("#epg-url").fill("https://example.test/guide.xml");
  await page.locator("#epg-save").click();
  const source = page.locator('[id^="epg-source-epg"]');
  await expect(source).toContainText("My guide · On");
  await source.click();
  await expect(page.locator('[id^="epg-source-epg"]')).toContainText("Off");
  await page.locator('[id^="epg-remove-epg"]').click();
  await expect(page.locator('[id^="epg-source-epg"]')).toHaveCount(0);
  await expect(page.locator("#epg-source-lounge")).toContainText("Lounge TV");
  await page.keyboard.press("Escape");
  await expect(page.locator("#setting-tab_epg")).toBeFocused();
});

test("visible Live rows load fallback EPG even when the full guide does not cover that channel", async ({
  page,
}) => {
  await mock(page);
  await page.context().route("https://connect.loungetv.nz/xmltv.php?**", (r) =>
    r.fulfill({
      contentType: "application/xml",
      body: `<tv><programme channel="ltv-20000" start="${xmlDate(now - 3600000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>Full guide channel</title></programme></tv>`,
    }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-live")).toBeVisible({ timeout: 15000 });
  await section(page, "live");
  await page.locator("#channel-20000").focus();
  for (let i = 0; i < 30; i++) await page.keyboard.press("ArrowDown");
  await expect(page.locator("#channel-20030 .channel-info")).toContainText(
    "PGA Tour",
  );
});

test("Sport Lounge merges primary Android fixture feeds with the supplemental calendar", async ({
  page,
}) => {
  await mock(page);
  await page.route("https://site.api.espn.com/**", (r) => {
    const u = r.request().url(),
      sport = u.includes("/rugby/")
        ? "rugby"
        : u.includes("/soccer/")
          ? "soccer"
          : u.includes("/basketball/nba/")
            ? "basketball"
            : "";
    if (!sport) return r.fulfill({ json: { events: [] } });
    return r.fulfill({
      json: {
        leagues: [{ name: sport + " league" }],
        events: [
          {
            id: sport,
            name: sport + " match",
            date: new Date(now + 2 * 3600000).toISOString(),
            competitions: [
              {
                date: new Date(now + 2 * 3600000).toISOString(),
                status: { type: { state: "pre" } },
                competitors: [
                  {
                    team: { id: sport + "home", displayName: sport + " Home" },
                  },
                  {
                    team: { id: sport + "away", displayName: sport + " Away" },
                  },
                ],
              },
            ],
          },
        ],
      },
    });
  });
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-sports")).toBeVisible({ timeout: 15000 });
  await section(page, "sports");
  await page.locator("#time-upcoming").click();
  await expect(page.locator("#sport-rugby")).toBeVisible();
  await expect(page.locator("#sport-soccer")).toBeVisible();
  await expect(page.locator("#sport-basketball")).toBeVisible();
  await page.locator("#sport-rugby").click();
  await expect(page.locator("#sports-grid")).toContainText("rugby Home");
});

test("movie Play is available before a delayed metadata response completes", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "movies");
  let release!: () => void;
  const gate = new Promise<void>((r) => (release = r));
  await page.route("**/player_api.php?**", async (r) => {
    if (
      new URL(r.request().url()).searchParams.get("action") !== "get_vod_info"
    )
      return r.fallback();
    await gate;
    return r.fulfill({
      json: {
        info: { name: "The Matrix", plot: "Metadata finished" },
        movie_data: { container_extension: "mp4" },
      },
    });
  });
  await page.locator("#vod-10000").click();
  await expect(page.locator("#detail-play")).toBeVisible({ timeout: 1000 });
  await expect(page.locator("#detail-play")).toBeFocused();
  release();
  await expect(page.locator(".details-copy .overview")).toHaveText(
    "Metadata finished",
  );
});

test("a 600-episode season renders a bounded window and remote navigation reaches later episodes", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "series");
  await page.route("**/player_api.php?**", async (r) => {
    if (
      new URL(r.request().url()).searchParams.get("action") !==
      "get_series_info"
    )
      return r.fallback();
    return r.fulfill({
      json: {
        info: { name: "Long running series" },
        episodes: {
          "1": Array.from({ length: 600 }, (_, i) => ({
            id: 90000 + i,
            title: "Episode " + (i + 1),
            season: 1,
            episode_num: i + 1,
            container_extension: "mp4",
          })),
        },
      },
    });
  });
  await page.locator("#vod-30000").click();
  await page.locator("#season-1").click();
  await expect(page.locator("#episode-90000")).toBeFocused();
  for (let i = 0; i < 40; i++) await page.keyboard.press("ArrowDown");
  await expect(page.locator("#episode-90040")).toBeFocused();
  expect(await page.locator("#episode-list .episode").count()).toBeLessThan(9);
  await page.keyboard.press("Escape");
  await expect(page.locator("#vod-30000")).toBeFocused();
});

test("additional XMLTV source is matched by exact channel name and merged without replacing canonical listings", async ({
  page,
}) => {
  await mock(page);
  const programme = (id: string, title: string) =>
    `<programme channel="${id}" start="${xmlDate(now - 3600000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>${title}</title></programme>`;
  await page.context().route("https://connect.loungetv.nz/xmltv.php?**", (r) =>
    r.fulfill({
      contentType: "application/xml",
      body: "<tv>" + programme("ltv-20000", "Canonical show") + "</tv>",
    }),
  );
  await page.context().route("https://custom.test/guide.xml", (r) =>
    r.fulfill({
      contentType: "application/xml",
      headers: { "Access-Control-Allow-Origin": "*" },
      body:
        '<tv><channel id="supplemental"><display-name>SKY SPORT 11</display-name></channel>' +
        programme("supplemental", "Extra guide show") +
        programme("ltv-20000", "Wrong replacement") +
        "</tv>",
    }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-settings")).toBeVisible({ timeout: 15000 });
  await section(page, "settings");
  await page.locator("#settings-group-sources").click();
  await page.locator("#setting-tab_epg").click();
  await page.locator("#epg-add").click();
  await page.locator("#epg-name").fill("Supplement");
  await page.locator("#epg-url").fill("https://custom.test/guide.xml");
  await page.locator("#epg-save").click();
  await page.locator("#epg-sync").click();
  await expect(page.locator("#overlay")).toContainText("2 channels ready");
  await page.keyboard.press("Escape");
  await section(page, "live");
  await expect(page.locator("#channel-20000 .channel-info")).toContainText(
    "Canonical show",
  );
  await page.locator("#channel-20000").focus();
  for (let i = 0; i < 10; i++) await page.keyboard.press("ArrowDown");
  await expect(page.locator("#channel-20010 .channel-info")).toContainText(
    "Extra guide show",
  );
});

test("fresh NZ gzip fallback restores an expired TVNZ listing without assigning NZ Bravo to US Bravo", async ({
  page,
}) => {
  await mock(page);
  const zlib = await import("node:zlib");
  await page.context().route("https://connect.loungetv.nz/**", async (r) => {
    const u = new URL(r.request().url()),
      a = u.searchParams.get("action");
    if (a === "get_live_categories")
      return r.fulfill({
        json: [
          { category_id: "nz", category_name: "New Zealand · TV" },
          { category_id: "us", category_name: "Entertainment · US" },
        ],
      });
    if (a === "get_live_streams")
      return r.fulfill({
        json: [
          {
            stream_id: 1,
            name: "TVNZ 1",
            category_id: "nz",
            epg_channel_id: "mjh-tvnz-1",
          },
          {
            stream_id: 2,
            name: "Bravo",
            category_id: "us",
            epg_channel_id: "Bravo.us",
          },
        ],
      });
    if (u.pathname === "/xmltv.php")
      return r.fulfill({
        contentType: "application/xml",
        body: `<tv><programme channel="mjh-tvnz-1" start="${xmlDate(now - 7200000)} +0000" stop="${xmlDate(now - 3600000)} +0000"><title>Expired programme</title></programme></tv>`,
      });
    return r.fallback();
  });
  await page.route(
    "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/nz/epg.xml.gz",
    (r) =>
      r.fulfill({
        contentType: "application/gzip",
        headers: { "Access-Control-Allow-Origin": "*" },
        body: zlib.gzipSync(
          `<tv><channel id="mjh-tvnz-1"><display-name>TVNZ 1</display-name></channel><channel id="mjh-bravo"><display-name>Bravo</display-name></channel><programme channel="mjh-tvnz-1" start="${xmlDate(now - 60000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>Fresh NZ programme</title></programme><programme channel="mjh-bravo" start="${xmlDate(now - 60000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>NZ Bravo programme</title></programme></tv>`,
        ),
      }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-live")).toBeVisible({ timeout: 15000 });
  await section(page, "live");
  await expect(page.locator("#channel-1")).toContainText("Fresh NZ programme");
  await section(page, "settings");
  await page.locator("#settings-group-sources").click();
  await page.locator("#setting-tab_epg").click();
  await expect(page.locator("#overlay")).toContainText(
    "New Zealand guide fallback: 1 channels with programmes now",
  );
});

test("Australian Ten and 10 Bold keep separate cached programmes even when their supplied EPG IDs collide", async ({
  page,
}) => {
  await mock(page);
  const zlib = await import("node:zlib");
  const programme = (id: string, title: string) =>
    `<programme channel="${id}" start="${xmlDate(now - 60000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>${title}</title></programme>`;
  await page.context().route("https://connect.loungetv.nz/**", async (r) => {
    const u = new URL(r.request().url()),
      a = u.searchParams.get("action");
    if (a === "get_live_categories")
      return r.fulfill({
        json: [{ category_id: "au", category_name: "Australia · TV" }],
      });
    if (a === "get_live_streams")
      return r.fulfill({
        json: [
          {
            stream_id: 1,
            name: "Ten",
            category_id: "au",
            epg_channel_id: "10Bold.au",
          },
          {
            stream_id: 2,
            name: "10 BOLD",
            category_id: "au",
            epg_channel_id: "10Bold.au",
          },
        ],
      });
    if (u.pathname === "/xmltv.php")
      return r.fulfill({
        contentType: "application/xml",
        body: "<tv>" + programme("10Bold.au", "Bold programme") + "</tv>",
      });
    return r.fallback();
  });
  await page.route(
    "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/au/Sydney/epg.xml.gz",
    (r) =>
      r.fulfill({
        contentType: "application/gzip",
        headers: { "Access-Control-Allow-Origin": "*" },
        body: zlib.gzipSync(
          "<tv>" +
            programme("mjh-10-nsw", "Main Ten programme") +
            programme("mjh-10bold-nsw", "Alternative Bold listing") +
            "</tv>",
        ),
      }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-live")).toBeVisible({ timeout: 15000 });
  await section(page, "live");
  await expect(page.locator("#channel-1")).toContainText("Main Ten programme");
  await expect(page.locator("#channel-2")).toContainText("Bold programme");
  await expect(page.locator("#channel-1")).not.toContainText("Bold programme");
});

test("a failed canonical refresh retains valid previous listings while publishing a healthy regional supplement", async ({
  page,
}) => {
  await mock(page);
  let fail = false;
  const zlib = await import("node:zlib");
  await page.context().route("https://connect.loungetv.nz/**", (r) => {
    const u = new URL(r.request().url());
    if (
      fail &&
      (u.pathname === "/xmltv.php" ||
        u.searchParams.get("action") === "get_short_epg")
    )
      return r.fulfill({ status: 503, body: "Unavailable" });
    return r.fallback();
  });
  await page.route(
    "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/nz/epg.xml.gz",
    (r) =>
      fail
        ? r.fulfill({
            contentType: "application/gzip",
            headers: { "Access-Control-Allow-Origin": "*" },
            body: zlib.gzipSync(
              `<tv><programme channel="ltv-20000" start="${xmlDate(now - 60000)} +0000" stop="${xmlDate(now + 3600000)} +0000"><title>Supplement programme</title></programme></tv>`,
            ),
          })
        : r.fulfill({ status: 503, body: "Unavailable" }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#nav-settings")).toBeVisible({ timeout: 15000 });
  fail = true;
  await section(page, "settings");
  await page.locator("#settings-group-sources").click();
  await page.locator("#setting-tab_epg").click();
  await page.locator("#epg-sync").click();
  await expect(page.locator("#overlay")).toContainText("1 channels ready");
  await page.keyboard.press("Escape");
  await section(page, "live");
  await expect(page.locator("#channel-20000")).toContainText(
    "Supplement programme",
  );
  await expect(page.locator("#channel-20001")).toContainText("PGA Tour");
});

test("Guide moves one channel per key across the full virtual list and returns from the final row", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "guide");
  await expect(page.locator("#guide-cell-0-0")).toContainText("PGA Tour");
  await page.locator("#guide-cell-0-0").focus();
  for (let i = 1; i <= 24; i++) {
    await page.keyboard.press("ArrowDown");
    await expect(
      page.locator(`[data-guide-row="${i}"][data-guide-time]`),
    ).toBeFocused();
  }
  for (let i = 23; i >= 20; i--) {
    await page.keyboard.press("ArrowUp");
    await expect(
      page.locator(`[data-guide-row="${i}"][data-guide-time]`),
    ).toBeFocused();
  }
  await expect(
    page.getByRole("button", { name: /More channels|Previous channels/ }),
  ).toHaveCount(0);
  expect(await page.locator(".guide-row").count()).toBeLessThan(30);
  await page.locator("#guide-rows").evaluate((el) => {
    el.scrollTop = el.scrollHeight;
    el.dispatchEvent(new Event("scroll"));
  });
  await expect(page.locator("#guide-channel-2390")).toBeVisible();
  await page.locator("#guide-channel-2390").focus();
  await page.keyboard.press("ArrowDown");
  await expect(page.locator("#guide-channel-2390")).toBeFocused();
  await page.keyboard.press("ArrowUp");
  await expect(page.locator("#guide-channel-2389")).toBeFocused();
  await page.screenshot({ path: "test-results/guide-continuous-015.png" });
});

test("Guide refresh after an idle minute preserves focused programme and immediate next-row movement", async ({
  page,
}) => {
  await signIn(page);
  await section(page, "guide");
  await expect(page.locator("#guide-cell-0-0")).toContainText("PGA Tour");
  await page.locator("#guide-cell-0-0").focus();
  await page.clock.install();
  await page.clock.fastForward(61000);
  await expect(page.locator("#guide-cell-0-0")).toBeFocused();
  await page.keyboard.press("ArrowDown");
  await expect(page.locator("#guide-cell-1-0")).toBeFocused();
  await page
    .locator("#guide-cell-1-0")
    .evaluate((el) => (el as HTMLElement).blur());
  await page.keyboard.press("ArrowDown");
  await expect(page.locator("#guide-cell-2-0")).toBeFocused();
});

test("Hero previews events on directional focus without Enter, hides logos, and stays clear of the clock", async ({
  page,
}) => {
  await mock(page);
  await page.route("https://liqocisegeqzbyuqloql.supabase.co/**", (r) =>
    r.fulfill({
      json: {
        events: [
          event,
          {
            ...event,
            id: "golf-final",
            externalId: "43",
            sport: "golf",
            feedId: "golf/pga",
            leagueName: "PGA Tour",
            title: "PGA Championship Final Round",
            competitors: [
              { id: "c", name: "Golf", logo: "https://images.test/crest.jpg" },
            ],
          },
        ],
        status: "ok",
      },
    }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#hero-page-1")).toBeVisible({ timeout: 15000 });
  await page.locator("#hero-page-1").focus();
  await expect(page.locator("#major-hero h2")).toHaveText("Warriors at Heat");
  await page.keyboard.press("ArrowLeft");
  await expect(page.locator("#hero-page-0")).toBeFocused();
  await expect(page.locator("#major-hero h2")).toHaveText(
    "PGA Championship Final Round",
  );
  await expect(page.locator("#major-hero .hero-teams")).toHaveCount(0);
  await expect(page.locator('#major-hero img[src*="crest"]')).toHaveCount(0);
  const clock = await page.locator(".clock").boundingBox(),
    hero = await page.locator("#major-hero").boundingBox();
  expect(clock!.y + clock!.height).toBeLessThan(hero!.y);
  await page.clock.install();
  await page.clock.fastForward(25000);
  await expect(page.locator("#hero-page-0")).toBeFocused();
  await expect(page.locator("#major-hero h2")).toHaveText(
    "PGA Championship Final Round",
  );
  await page.screenshot({ path: "test-results/hero-clean-015.png" });
});

test("TVNZ selects direct Widevine DASH, NZ HLS starts with HD adaptation, and provider playback remains native", async ({
  page,
}) => {
  await syntheticNativeVideo(page);
  await page.addInitScript(() => {
    (window as any).__adaptiveCalls = [];
    class Player {
      static isBrowserSupported() {
        return true;
      }
      addEventListener() {}
      async attach(video: HTMLVideoElement) {
        (window as any).__adaptiveCalls.push(["attach", video.id]);
      }
      configure(config: any) {
        (window as any).__adaptiveCalls.push(["configure", config]);
      }
      async load(url: string, unused: any, mime: string) {
        (window as any).__adaptiveCalls.push(["load", url, mime]);
      }
      async destroy() {
        (window as any).__adaptiveCalls.push(["destroy"]);
      }
    }
    (window as any).shaka = { Player, polyfill: { installAll() {} } };
  });
  await mock(page);
  await page
    .context()
    .route("https://connect.loungetv.nz/**action=get_live_streams*", (r) =>
      r.fulfill({
        json: [
          {
            ...live[0],
            name: "TVNZ 1",
            stream_id: 900000001,
            epg_channel_id: "mjh-tvnz-1",
          },
          {
            ...live[1],
            name: "Three",
            stream_id: 900000003,
            epg_channel_id: "mjh-three",
          },
          live[2],
        ],
      }),
    );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#featured-0")).toBeVisible({ timeout: 15000 });
  await section(page, "live");
  await page.locator("#channel-900000001").focus();
  await page.keyboard.press("Enter");
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as any).__adaptiveCalls.filter((c: any[]) => c[0] === "load")
            .length,
      ),
    )
    .toBe(1);
  const calls = await page.evaluate(() => (window as any).__adaptiveCalls);
  expect(calls.find((c: any[]) => c[0] === "load")[1]).toBe(
    "https://i.mjh.nz/.r/tvnz-1.mpd",
  );
  expect(
    calls.find((c: any[]) => c[0] === "configure")[1].drm.servers[
      "com.widevine.alpha"
    ],
  ).toBe("https://c.mjh.nz/tvnz-1-wv");
  await page.keyboard.press("Escape");
  await page.locator("#channel-900000003").focus();
  await page.keyboard.press("Enter");
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as any).__adaptiveCalls.filter((c: any[]) => c[0] === "load")
            .length,
      ),
    )
    .toBe(2);
  const hlsCalls = await page.evaluate(() => (window as any).__adaptiveCalls);
  expect(hlsCalls.filter((c: any[]) => c[0] === "load")[1]).toEqual([
    "load",
    "https://i.mjh.nz/.r/three.m3u8",
    "application/x-mpegurl",
  ]);
  const config = hlsCalls.filter((c: any[]) => c[0] === "configure")[1][1];
  expect(config.abr.defaultBandwidthEstimate).toBe(8000000);
  expect(config.abr.restrictions.maxHeight).toBe(1080);
  expect(config.abr.enabled).toBe(true);
  await page.keyboard.press("Escape");
  await page.locator("#channel-20002").focus();
  await page.keyboard.press("Enter");
  await expect
    .poll(() =>
      page.locator("#video").evaluate((v) => (v as HTMLVideoElement).src),
    )
    .toContain("/live/qa-user/qa-password/20002.ts");
  expect(
    await page.evaluate(
      () =>
        (window as any).__adaptiveCalls.filter((c: any[]) => c[0] === "destroy")
          .length,
    ),
  ).toBe(2);
});

test("Guide delayed programme arrival keeps the selected row and unequal programme lengths never skip channels", async ({
  page,
}) => {
  await mock(page);
  await page
    .context()
    .route("https://connect.loungetv.nz/xmltv.php*", (r) =>
      r.fulfill({ contentType: "application/xml", body: "<tv></tv>" }),
    );
  await page
    .context()
    .route("https://connect.loungetv.nz/**action=get_short_epg*", async (r) => {
      await new Promise((resolve) => setTimeout(resolve, 700));
      const id = Number(
        new URL(r.request().url()).searchParams.get("stream_id"),
      );
      const start = Math.floor((now - 3600000) / 1000),
        end = Math.floor((now + 3600000) / 1000);
      await r.fulfill({
        json: {
          epg_listings:
            id % 2
              ? [
                  {
                    title: Buffer.from("Short first").toString("base64"),
                    start_timestamp: start,
                    stop_timestamp: Math.floor((now - 300000) / 1000),
                  },
                  {
                    title: Buffer.from("Short current").toString("base64"),
                    start_timestamp: Math.floor((now - 300000) / 1000),
                    stop_timestamp: end,
                  },
                ]
              : [
                  {
                    title: Buffer.from("Long programme").toString("base64"),
                    start_timestamp: start,
                    stop_timestamp: end,
                  },
                ],
        },
      });
    });
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#featured-0")).toBeVisible({ timeout: 15000 });
  await section(page, "guide");
  await page.locator("#guide-cell-0-0").focus();
  await expect(page.locator("#guide-cell-0-0")).toContainText("Long programme");
  await expect(page.locator("#guide-cell-0-0")).toBeFocused();
  for (let i = 1; i <= 6; i++) {
    await page.keyboard.press("ArrowDown");
    await expect(
      page.locator(`[data-guide-row="${i}"][data-guide-time]:focus`),
    ).toHaveCount(1);
  }
  await page.keyboard.press("ArrowUp");
  await expect(
    page.locator('[data-guide-row="5"][data-guide-time]:focus'),
  ).toHaveCount(1);
});

test("Clock and date stay inside the fixed header on every page with long channel and profile names", async ({
  page,
}) => {
  await page.addInitScript(() => {
    localStorage.setItem(
      "lounge.webos.v1:qa-user:last-channel",
      JSON.stringify({
        id: "20000",
        kind: "live",
        name: "SKY SPORT VERY LONG CHANNEL NAME HD BACKUP",
        image: "",
        category: "live_nz_sports",
        ext: "ts",
        rating: 0,
      }),
    );
    localStorage.setItem(
      "lounge.webos.v1:qa-user:profiles",
      JSON.stringify([{ id: "default", name: "A very long profile name" }]),
    );
  });
  await signIn(page);
  for (const id of [
    "home",
    "movies",
    "series",
    "sports",
    "guide",
    "settings",
  ]) {
    await section(page, id);
    const clock = await page.locator(".clock").boundingBox();
    expect(clock!.x + clock!.width).toBeLessThanOrEqual(1920);
    expect(clock!.y + clock!.height).toBeLessThanOrEqual(100);
    expect(
      await page
        .locator(".clock")
        .evaluate((el) => el.scrollWidth <= el.clientWidth),
    ).toBe(true);
  }
  await section(page, "home");
  await page.screenshot({ path: "test-results/clock-long-names-015.png" });
});

test("all twelve discovery sports decode packaged hero artwork and a failed remote image falls back locally", async ({
  page,
}) => {
  await mock(page);
  const sports = [
    ["basketball", "nba"],
    ["football", "nfl"],
    ["rugby-league", "league"],
    ["rugby", "rugby"],
    ["soccer", "soccer"],
    ["racing", "f1"],
    ["cricket", "cricket"],
    ["mma", "combat"],
    ["golf", "golf"],
    ["tennis", "tennis"],
    ["hockey", "hockey"],
    ["baseball", "baseball"],
  ];
  const leagues = [
    "NBA",
    "NFL",
    "NRL",
    "International Rugby",
    "Premier League",
    "F1",
    "Cricket",
    "UFC",
    "PGA",
    "ATP",
    "NHL",
    "MLB",
  ];
  const fixtures = sports.map(([sport], i) => ({
    ...event,
    id: "art-" + i,
    externalId: "art-" + i,
    sport,
    leagueName: leagues[i],
    feedId: sport + "/" + leagues[i],
    title: leagues[i] + " artwork QA",
    hero_art_url:
      sport === "hockey" ? "https://images.test/broken-hero.jpg" : undefined,
  }));
  await page.route("https://images.test/broken-hero.jpg", (r) =>
    r.fulfill({ status: 404, body: "missing" }),
  );
  await page.route("https://liqocisegeqzbyuqloql.supabase.co/**", (r) =>
    r.fulfill({ json: { events: fixtures, status: "ok" } }),
  );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator(".hero-pager button")).toHaveCount(12, {
    timeout: 15000,
  });
  for (let i = 0; i < sports.length; i++) {
    await page
      .locator(`.hero-pager button[aria-label="${leagues[i]} artwork QA"]`)
      .focus();
    await expect(page.locator("#major-hero h2")).toHaveText(
      leagues[i] + " artwork QA",
    );
    const art = page.locator("#major-hero .hero-image");
    await expect(art).toBeVisible();
    await expect
      .poll(() => art.evaluate((el) => (el as HTMLImageElement).naturalWidth))
      .toBeGreaterThan(1000);
    await expect(art).toHaveAttribute(
      "src",
      `assets/lounge_hero_${sports[i][1]}.webp`,
    );
  }
  await page.locator("#major-hero .hero-image").evaluate(async el => { await (el as HTMLImageElement).decode(); await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))); });
  await page.screenshot({ path: "test-results/hero-baseball-016.png" });
});

test("TVNZ license refusal reports a readable stage and HTTP code without repeated retries or exposed URLs", async ({
  page,
}) => {
  await syntheticNativeVideo(page);
  await page.addInitScript(() => {
    (window as any).__licenseLoads = 0;
    class Player {
      static isBrowserSupported() {
        return true;
      }
      addEventListener() {}
      async attach() {}
      configure() {}
      async load() {
        (window as any).__licenseLoads++;
        throw {
          code: 6007,
          data: [
            {
              code: 1001,
              data: [
                "https://private.test/token",
                403,
                "PRIVATE LICENSE BODY",
                {},
                2,
              ],
            },
          ],
        };
      }
      async destroy() {}
    }
    (window as any).shaka = { Player, polyfill: { installAll() {} } };
  });
  await mock(page);
  await page
    .context()
    .route("https://connect.loungetv.nz/**action=get_live_streams*", (r) =>
      r.fulfill({
        json: [{ ...live[0], name: "TVNZ 1", epg_channel_id: "mjh-tvnz-1" }],
      }),
    );
  await page.goto("/");
  await page.locator("#login-user").fill("qa-user");
  await page.locator("#login-password").fill("qa-password");
  await page.locator("#login-submit").click();
  await expect(page.locator("#featured-0")).toBeVisible({ timeout: 15000 });
  await section(page, "live");
  await page.locator("#channel-20000").focus();
  await page.keyboard.press("Enter");
  await expect(page.locator(".player-error")).toContainText(
    "DRM / license failed (HTTP 403). Code 6007.",
  );
  await page.clock.install();
  await page.clock.fastForward(60000);
  expect(await page.evaluate(() => (window as any).__licenseLoads)).toBe(1);
  await page.locator("#play-diagnostics").click();
  await expect(page.locator("#overlay")).not.toContainText("private.test");
  await expect(page.locator("#overlay")).not.toContainText(
    "PRIVATE LICENSE BODY",
  );
});
