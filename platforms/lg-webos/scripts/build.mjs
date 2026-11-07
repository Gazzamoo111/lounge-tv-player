import { build } from "esbuild";
import { rm, cp, mkdir, copyFile, writeFile } from "node:fs/promises";
await rm("dist", { recursive: true, force: true });
await mkdir("dist", { recursive: true });
await cp("public", "dist", { recursive: true });
await copyFile("src/app.css", "dist/app.css");
await build({
  entryPoints: ["src/app.ts"],
  bundle: true,
  outfile: "dist/app.js",
  target: "chrome87",
  format: "iife",
  minify: true,
  legalComments: "eof",
});
await build({
  entryPoints: ["src/epg.worker.ts"],
  bundle: true,
  outfile: "dist/epg.worker.js",
  target: "chrome87",
  format: "iife",
  minify: true,
});
await mkdir("dist/vendor", { recursive: true });
await copyFile(
  "node_modules/shaka-player/dist/shaka-player.compiled.js",
  "dist/vendor/shaka-player.compiled.js",
);
await copyFile(
  "node_modules/shaka-player/LICENSE",
  "dist/vendor/SHAKA-LICENSE.txt",
);
console.log("Built LG application. No backend or Android changes.");

await writeFile(
  "dist/vendor/QR-LICENSE.txt",
  `QR Code Generator for JavaScript
Copyright (c) 2009 Kazuhiko Arase
https://github.com/kazuhikoarase/qrcode-generator

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
`,
);
