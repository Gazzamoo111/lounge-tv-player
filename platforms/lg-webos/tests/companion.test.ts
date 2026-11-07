import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const { createCompanion } = createRequire(import.meta.url)(
  "../services/companion/core.js",
);
const start = (c: any): Promise<any> =>
  new Promise((resolve, reject) =>
    c.start((e: any, r: any) => (e ? reject(e) : resolve(r))),
  );
test("phone sign-in is nonce protected, one use, closes after TV collection and never puts credentials in the QR URL", async () => {
  const c = createCompanion({ address: "127.0.0.1", bind: "127.0.0.1" });
  try {
    const s = await start(c),
      base = s.url.split("#")[0];
    assert.equal(s.token.length, 64);
    const page = await fetch(base);
    assert.equal(page.status, 200);
    assert.match(await page.text(), /Sign in on your TV/);
    assert.equal(page.headers.get("Cache-Control"), "no-store");
    const post = (v: any, origin?: string) =>
      fetch(new URL("/sign-in", base), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(origin ? { Origin: origin } : {}),
        },
        body: JSON.stringify(v),
      });
    assert.equal(
      (await post({ token: "bad", username: "customer", password: "secret" }))
        .status,
      403,
    );
    assert.equal(
      (
        await post(
          { token: s.token, username: "customer", password: "secret" },
          "https://attacker.test",
        )
      ).status,
      403,
    );
    assert.equal(
      (await post({ token: s.token, username: "customer", password: "" }))
        .status,
      400,
    );
    assert.equal(c.take("bad").returnValue, false);
    assert.equal(c.take(s.token).pending, true);
    assert.equal(
      (await post({ token: s.token, username: "customer", password: "secret" }))
        .status,
      200,
    );
    assert.equal(
      (await post({ token: s.token, username: "other", password: "different" }))
        .status,
      409,
    );
    assert.deepEqual(c.take(s.token).credentials, {
      username: "customer",
      password: "secret",
    });
    assert.equal(c.take(s.token).returnValue, false);
    assert.ok(!s.url.includes("customer") && !s.url.includes("secret"));
  } finally {
    c.close();
  }
});
test("refreshing the phone QR invalidates the old token and cancellation removes submitted credentials", async () => {
  const c = createCompanion({ address: "127.0.0.1", bind: "127.0.0.1" });
  try {
    const first = await start(c),
      second = await start(c);
    assert.notEqual(first.token, second.token);
    assert.equal(c.take(first.token).returnValue, false);
    c.stop(second.token);
    assert.equal(c.take(second.token).returnValue, false);
  } finally {
    c.close();
  }
});
