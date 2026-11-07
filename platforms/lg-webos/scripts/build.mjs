import { releaseConfig } from "./release-config.mjs";
import { build } from "esbuild";
import { rm, cp, mkdir, copyFile, writeFile } from "node:fs/promises";
const config = releaseConfig();
const dist = config.dist;
await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
await cp("public", dist, { recursive: true });
await copyFile("src/app.css", dist + "/app.css");
await build({
  entryPoints: ["src/app.ts"],
  bundle: true,
  define: {
    __LOUNGE_APP_ID__: JSON.stringify(config.id),
    __LOUNGE_APP_VERSION__: JSON.stringify(config.version),
    __LOUNGE_CUSTOMER__: JSON.stringify(config.customer),
  },
  outfile: dist + "/app.js",
  target: "chrome87",
  format: "iife",
  minify: true,
  legalComments: "eof",
});
await build({
  entryPoints: ["src/epg.worker.ts"],
  bundle: true,
  outfile: dist + "/epg.worker.js",
  target: "chrome87",
  format: "iife",
  minify: true,
});
await mkdir(dist + "/vendor", { recursive: true });
await copyFile(
  "node_modules/shaka-player/dist/shaka-player.compiled.js",
  dist + "/vendor/shaka-player.compiled.js",
);
await copyFile(
  "node_modules/shaka-player/LICENSE",
  dist + "/vendor/SHAKA-LICENSE.txt",
);
console.log("Built LG application. No backend or Android changes.");

await writeFile(
  dist + "/vendor/QR-LICENSE.txt",
  `QR Code Generator for JavaScript
Copyright (c) 2009 Kazuhiko Arase
https://github.com/kazuhikoarase/qrcode-generator

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
`,
);

const meta = JSON.parse(
  await (
    await import("node:fs/promises")
  ).readFile(dist + "/appinfo.json", "utf8"),
);
meta.id = config.id;
meta.version = config.version;
meta.title = config.title;
meta.iconColor = "#050a14";
if (config.customer) meta.splashBackground = "splashBackground.png";
await writeFile(dist + "/appinfo.json", JSON.stringify(meta, null, 2) + "\n");
if (config.customer) {
  await rm(config.service, { recursive: true, force: true });
  await cp("services/companion", config.service, { recursive: true });
  const fs = await import("node:fs/promises");
  const pkg = JSON.parse(
    await fs.readFile(config.service + "/package.json", "utf8"),
  );
  pkg.name = config.id + ".companion";
  pkg.version = config.version;
  await writeFile(
    config.service + "/package.json",
    JSON.stringify(pkg, null, 2) + "\n",
  );
  const services = JSON.parse(
    await fs.readFile(config.service + "/services.json", "utf8"),
  );
  services.id = pkg.name;
  services.services.forEach((service) => {
    service.name = pkg.name;
  });
  await writeFile(
    config.service + "/services.json",
    JSON.stringify(services, null, 2) + "\n",
  );
}
console.log(`${config.title} ${config.version}: ${config.id}`);
