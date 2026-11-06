import { registerHooks } from "node:module";
import { transformSync } from "esbuild";
import { readFileSync } from "node:fs";
registerHooks({
  resolve(specifier, ctx, next) {
    if (specifier.startsWith(".") && !/\.[a-z]+$/i.test(specifier))
      return next(specifier + ".ts", ctx);
    return next(specifier, ctx);
  },
  load(url, ctx, next) {
    if (url.endsWith(".ts"))
      return {
        format: "module",
        source: transformSync(readFileSync(new URL(url), "utf8"), {
          loader: "ts",
          format: "esm",
          target: "node24",
        }).code,
        shortCircuit: true,
      };
    return next(url, ctx);
  },
});
