import { execFileSync } from "node:child_process";
import {
  readFileSync,
  mkdtempSync,
  rmSync,
  existsSync,
  readdirSync,
} from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
const version = JSON.parse(readFileSync("public/appinfo.json", "utf8")).version;
const file = `artifacts/nz.loungetv.webos.qa_${version}_all.ipk`;
const work = mkdtempSync(join(tmpdir(), "lounge-ipk-"));
try {
  execFileSync("ar", ["x", join(process.cwd(), file)], { cwd: work });
  assert.equal(readFileSync(join(work, "debian-binary"), "utf8").trim(), "2.0");
  const tar = readdirSync(work).find((x) => x.startsWith("data.tar"));
  assert.ok(tar);
  execFileSync("tar", ["-xf", join(work, tar), "-C", work]);
  const base = join(work, "usr/palm/applications/nz.loungetv.webos.qa");
  const meta = JSON.parse(readFileSync(join(base, "appinfo.json"), "utf8"));
  assert.equal(meta.id, "nz.loungetv.webos.qa");
  assert.equal(meta.version, version);
  assert.equal(meta.handlesRelaunch, false);
  for (const name of [
    "epg.worker.js",
    "vendor/shaka-player.compiled.js",
    "vendor/SHAKA-LICENSE.txt",
    "vendor/QR-LICENSE.txt",
    "largeIcon.png",
    "index.html",
    "app.js",
    "app.css",
    "icon.png",
    "assets/jakarta.ttf",
    "assets/nav-mark.png",
    ...[
      "nba",
      "nfl",
      "league",
      "rugby",
      "soccer",
      "f1",
      "cricket",
      "combat",
      "golf",
      "tennis",
      "hockey",
      "baseball",
    ].map((sport) => `assets/lounge_hero_${sport}.webp`),
  ])
    assert.ok(existsSync(join(base, name)), `Missing ${name}`);
  const companion = join(
    work,
    "usr/palm/services/nz.loungetv.webos.qa.companion",
  );
  for (const name of [
    "package.json",
    "services.json",
    "index.js",
    "core.js",
    "license.js",
    "phone.html",
    "phone.js",
  ])
    assert.ok(existsSync(join(companion, name)), `Missing companion ${name}`);
  const service = JSON.parse(
    readFileSync(join(companion, "package.json"), "utf8"),
  );
  assert.equal(service.name, meta.id + ".companion");
  assert.ok(
    readFileSync(join(base, "index.html"), "utf8").includes("boot-splash"),
  );
  const js = readFileSync(join(base, "app.js"), "utf8");
  assert.ok(js.includes("https://connect.loungetv.nz"));
  assert.ok(!/line\.bestnz|service_role|SUPABASE_DB_URL|qa-password/.test(js));
  console.log(
    "IPK verified: ar/control/data structure, application identity, entrypoint, bundled assets, gateway, no fixture credentials.",
  );
  console.log(
    "SHA256 " + createHash("sha256").update(readFileSync(file)).digest("hex"),
  );
} finally {
  rmSync(work, { recursive: true, force: true });
}
