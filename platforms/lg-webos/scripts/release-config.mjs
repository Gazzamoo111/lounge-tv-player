import { readFileSync } from "node:fs";
export function releaseConfig(env = process.env) {
  const qa = JSON.parse(
    readFileSync(new URL("../public/appinfo.json", import.meta.url), "utf8"),
  );
  if (env.LOUNGE_CHANNEL && !["qa", "customer"].includes(env.LOUNGE_CHANNEL))
    throw Error("Unknown release channel");
  const customer = env.LOUNGE_CHANNEL === "customer";
  const id = customer
    ? env.LOUNGE_CUSTOMER_APP_ID || "nz.loungetv.webos"
    : qa.id;
  const version = customer
    ? env.LOUNGE_CUSTOMER_VERSION || "1.0.0"
    : qa.version;
  if (!/^[a-z][a-z0-9]*(?:\.[a-z0-9]+)+$/.test(id))
    throw Error(
      "App ID must be lowercase dotted identifiers, compatible with the bundled JS service.",
    );
  if (/^(com\.(palm|webos|lge|palmdts))(\.|$)/.test(id))
    throw Error("Reserved webOS app identity");
  if (!/^\d+\.\d+\.\d+$/.test(version))
    throw Error("webOS requires a numeric three-part version.");
  if (customer && id.endsWith(".qa"))
    throw Error("Customer builds cannot use the QA identity.");
  return {
    customer,
    id,
    version,
    title: customer ? "Lounge TV" : qa.title,
    dist: customer ? "dist-customer" : "dist",
    service: customer ? "build-customer/companion" : "services/companion",
  };
}
