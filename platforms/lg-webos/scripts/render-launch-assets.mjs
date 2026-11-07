import { chromium } from "@playwright/test";
import { resolve } from "node:path";
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE,
});
try {
  const page = await browser.newPage({
    viewport: { width: 1920, height: 1080 },
  });
  await page.clock.install();
  await page.clock.pauseAt(new Date());
  await page.goto("file://" + resolve("dist-customer/index.html"));
  await page.locator("#boot-splash img").evaluate((img) => img.decode());
  await page.evaluate(() => document.fonts.ready);
  await page.addStyleTag({
    content: ".boot-splash span,.boot-splash i{visibility:hidden}",
  });
  await page.screenshot({ path: "public/splashBackground.png" });
  await page.setViewportSize({ width: 400, height: 400 });
  await page.setContent(
    '<style>html,body{margin:0;width:400px;height:400px;overflow:hidden;background:#050a14}img{width:400px;height:400px;object-fit:contain}</style><img id="store-icon">',
  );
  await page.locator("img").evaluate(
    (img, url) => {
      img.src = url;
      return img.decode();
    },
    "file://" + resolve("public/assets/brand.png"),
  );
  await page.screenshot({ path: "public/store-icon.png" });
} finally {
  await browser.close();
}
