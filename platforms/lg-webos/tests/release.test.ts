import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { releaseConfig } from "../scripts/release-config.mjs";
test("customer and QA build identities are isolated and validate before packaging", () => {
  const qa = releaseConfig({});
  const customer = releaseConfig({ LOUNGE_CHANNEL: "customer" });
  assert.equal(qa.id, "nz.loungetv.webos.qa");
  assert.equal(customer.id, "nz.loungetv.webos");
  assert.equal(customer.version, "1.0.0");
  assert.notEqual(qa.dist, customer.dist);
  assert.notEqual(qa.service, customer.service);
  assert.throws(
    () => releaseConfig({ LOUNGE_CHANNEL: "production" }),
    /Unknown release/,
  );
  assert.throws(
    () =>
      releaseConfig({
        LOUNGE_CHANNEL: "customer",
        LOUNGE_CUSTOMER_APP_ID: "com.lge.demo",
      }),
    /Reserved/,
  );
  assert.throws(
    () =>
      releaseConfig({
        LOUNGE_CHANNEL: "customer",
        LOUNGE_CUSTOMER_APP_ID: qa.id,
      }),
    /QA identity/,
  );
  assert.throws(
    () =>
      releaseConfig({
        LOUNGE_CHANNEL: "customer",
        LOUNGE_CUSTOMER_APP_ID: "invalid-id",
      }),
    /App ID/,
  );
});
test("each packaged service accepts its own app and refuses the other release identity", () => {
  const source = readFileSync(
    new URL("../services/companion/index.js", import.meta.url),
    "utf8",
  );
  for (const owner of ["nz.loungetv.webos", "nz.loungetv.webos.qa"]) {
    const handlers: Record<string, Function> = {};
    const companion = {
      start: (done: Function) =>
        done(null, { returnValue: true, token: "test" }),
      take: () => ({ returnValue: true }),
      stop: () => ({ returnValue: true }),
    };
    runInNewContext(source, {
      require: (name: string) => {
        if (name === "webos-service")
          return function () {
            return {
              register: (key: string, handler: Function) => {
                handlers[key] = handler;
              },
            };
          };
        if (name === "./package.json") return { name: owner + ".companion" };
        if (name === "./core") return { createCompanion: () => companion };
        if (name === "./license")
          return {
            requestLicense: (_: unknown, done: Function) =>
              done({ returnValue: true }),
          };
        throw Error(name);
      },
    });
    for (const method of ["start", "license"]) {
      let allowed: any;
      handlers[method]({
        sender: owner,
        payload: {},
        respond: (r: any) => (allowed = r),
      });
      assert.equal(allowed.returnValue, true);
      let denied: any;
      handlers[method]({
        sender: owner.endsWith(".qa")
          ? "nz.loungetv.webos"
          : "nz.loungetv.webos.qa",
        payload: {},
        respond: (r: any) => (denied = r),
      });
      assert.equal(denied.returnValue, false);
    }
  }
});
