import { build } from "esbuild";
import { rm, cp, mkdir, copyFile } from "node:fs/promises";
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
console.log("Built LG application. No backend or Android changes.");
