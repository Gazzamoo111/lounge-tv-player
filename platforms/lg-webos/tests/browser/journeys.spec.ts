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
async function mock(page: Page) {
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
  await page.route("https://connect.loungetv.nz/**", (r) => {
    const u = new URL(r.request().url()),
      a = u.searchParams.get("action");
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
  await expect(page.locator("#featured-0")).toBeVisible();
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
  await expect(page.locator("#category-live_nz_sports")).toBeFocused();
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
  await expect(page.locator(".login .error")).toContainText(
    "check your Lounge TV login",
  );
  await expect(page.locator("#login-submit")).toBeEnabled();
  await expect(page.locator("main")).toHaveCount(0);
});
