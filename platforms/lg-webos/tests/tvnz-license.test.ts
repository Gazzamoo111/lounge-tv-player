import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { EventEmitter } from "node:events";
const { requestLicense, allowed } = createRequire(import.meta.url)(
  "../services/companion/license.js",
);
const payload = {
  channel: "tvnz-1",
  challenge: Buffer.from([1, 2, 3, 4]).toString("base64"),
};
test("normal TVNZ license transport preserves POST bytes and headers across a validated redirect", async () => {
  const seen: any[] = [];
  const result: any = await new Promise((resolve) =>
    requestLicense(payload, resolve, (options: any, callback: any) => {
      const request: any = new EventEmitter();
      request.destroy = () => {};
      request.end = (body: Buffer) => {
        seen.push({ options, body });
        const response: any = new EventEmitter();
        response.resume = () => {};
        response.destroy = () => {};
        if (seen.length === 1) {
          response.statusCode = 307;
          response.headers = {
            location:
              "https://fvnz-capi-prod.switch.tv/playback-api/getlicense?auth=private-server-token",
          };
          callback(response);
        } else {
          response.statusCode = 200;
          response.headers = { "content-type": "application/octet-stream" };
          callback(response);
          response.emit("data", Buffer.from([9, 8, 7]));
          response.emit("end");
        }
      };
      return request;
    }),
  );
  assert.equal(result.returnValue, true);
  assert.deepEqual(Buffer.from(result.data, "base64"), Buffer.from([9, 8, 7]));
  assert.equal(seen.length, 2);
  for (const row of seen) {
    assert.equal(row.options.method, "POST");
    assert.equal(
      row.options.headers["Content-Type"],
      "application/octet-stream",
    );
    assert.deepEqual(row.body, Buffer.from([1, 2, 3, 4]));
  }
  assert.ok(!JSON.stringify(result).includes("private-server-token"));
});
test("license proxy cannot be used as an arbitrary URL fetcher and rejects unsupported redirects", async () => {
  for (const url of [
    "http://c.mjh.nz/tvnz-1-wv",
    "https://c.mjh.nz/anything",
    "https://fvnz-capi-prod.switch.tv/anything",
    "https://127.0.0.1/private",
    "https://c.mjh.nz:8443/tvnz-1-wv",
    "https://user:password@c.mjh.nz/tvnz-1-wv",
  ])
    assert.equal(allowed(url), false, url);
  const result: any = await new Promise((resolve) =>
    requestLicense(payload, resolve, (_: any, callback: any) => {
      const request: any = new EventEmitter();
      request.destroy = () => {};
      request.end = () => {
        const response: any = {
          statusCode: 307,
          headers: { location: "https://attacker.test/private" },
          resume() {},
        };
        callback(response);
      };
      return request;
    }),
  );
  assert.equal(result.returnValue, false);
  assert.equal(result.errorCode, 9030);
});
test("TVNZ HTTP 502 remains a real server failure, with no license body or signed URL in the diagnostic result", async () => {
  const result: any = await new Promise((resolve) =>
    requestLicense(payload, resolve, (_: any, callback: any) => {
      const request: any = new EventEmitter();
      request.destroy = () => {};
      request.end = () =>
        callback({ statusCode: 502, headers: {}, resume() {} });
      return request;
    }),
  );
  assert.equal(result.errorCode, 6007);
  assert.equal(result.httpStatus, 502);
  assert.equal(result.data, undefined);
  assert.ok(!JSON.stringify(result).includes("challenge"));
});
