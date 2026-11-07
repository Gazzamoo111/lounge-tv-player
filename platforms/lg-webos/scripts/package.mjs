import { execFileSync } from "node:child_process";
import { releaseConfig } from "./release-config.mjs";
const config = releaseConfig();
execFileSync("npm", ["run", "build"], { stdio: "inherit" });
execFileSync("ares-package", [config.dist, config.service, "-o", "artifacts"], {
  stdio: "inherit",
});
execFileSync(process.execPath, ["scripts/verify-package.mjs"], {
  stdio: "inherit",
});
