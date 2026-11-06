import { test } from "node:test";
import assert from "node:assert/strict";
import {
  adaptiveFault,
  adaptiveFailureMessage,
} from "../src/adaptive-fault.ts";
test("DRM diagnostics preserve numeric phase/status while excluding signed URLs, bodies and headers", () => {
  const network = {
    code: 1001,
    data: [
      "https://secret.test/?token=PRIVATE",
      403,
      "private body",
      { Authorization: "private" },
      2,
    ],
  };
  const fault = adaptiveFault({ code: 6007, data: [network] });
  assert.equal(fault.phase, "license");
  assert.equal(fault.status, 403);
  assert.equal(fault.code, 6007);
  assert.match(adaptiveFailureMessage(fault), /DRM.*HTTP 403.*6007/);
  assert.ok(!JSON.stringify(fault).includes("PRIVATE"));
  assert.ok(!JSON.stringify(fault).includes("private"));
  assert.equal(
    adaptiveFault({ ...network, data: [...network.data.slice(0, 4), 0] }).phase,
    "manifest",
  );
});
