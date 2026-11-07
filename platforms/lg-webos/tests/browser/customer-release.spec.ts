import { test, expect } from "@playwright/test";
test.skip(
  process.env.LOUNGE_CHANNEL !== "customer",
  "Customer identity checks apply to the customer build.",
);
test("customer identity, native launch assets and Login Back belong to the production app", async ({
  page,
  request,
}) => {
  const meta = await (await request.get("/appinfo.json")).json();
  expect(meta.id).toBe("nz.loungetv.webos");
  expect(meta.title).toBe("Lounge TV");
  expect(meta.version).toBe("1.0.0");
  expect(meta.splashBackground).toBe("splashBackground.png");
  await page.addInitScript(() => {
    (window as any).__backCalls = 0;
    (window as any).webOS = {
      platformBack: () => {
        (window as any).__backCalls++;
      },
    };
  });
  await page.clock.install();
  await page.clock.pauseAt(new Date());
  await page.goto("/");
  await page
    .locator("#boot-splash img")
    .evaluate((img) => (img as HTMLImageElement).decode());
  const dimensions = await page.evaluate(async () => {
    const values = [];
    for (const path of ["splashBackground.png", "store-icon.png"]) {
      const img = new Image();
      img.src = path;
      await img.decode();
      values.push([img.naturalWidth, img.naturalHeight]);
    }
    return values;
  });
  expect(dimensions).toEqual([
    [1920, 1080],
    [400, 400],
  ]);
  await page.clock.fastForward(6000);
  await expect(page.locator("#login-phone")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect
    .poll(() => page.evaluate(() => (window as any).__backCalls))
    .toBe(1);
});
