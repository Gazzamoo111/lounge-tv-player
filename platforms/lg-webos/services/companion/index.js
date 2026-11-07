"use strict";
var Service = require("webos-service");
var service = new Service(require("./package.json").name);
var companion = require("./core").createCompanion();
var owner;
function permitted(message) {
  return (
    message.sender ===
    require("./package.json").name.replace(/\.companion$/, "")
  );
}
service.register("start", function (message) {
  if (!permitted(message))
    return message.respond({
      returnValue: false,
      errorText: "Only Lounge TV can start phone sign-in.",
    });
  owner = message.sender;
  companion.start(function (error, result) {
    message.respond(
      error ? { returnValue: false, errorText: error.message } : result,
    );
  });
});
service.register("take", function (message) {
  if (!permitted(message) || message.sender !== owner)
    return message.respond({ returnValue: false, errorText: "Not authorised" });
  message.respond(companion.take(message.payload.token));
});
service.register("stop", function (message) {
  if (!permitted(message) || message.sender !== owner)
    return message.respond({ returnValue: false, errorText: "Not authorised" });
  message.respond(companion.stop(message.payload.token));
});
// Normal Widevine challenges and responses remain opaque; only this TV app may request transport.
service.register("license", function (message) {
  if (!permitted(message))
    return message.respond({
      returnValue: false,
      errorCode: 9030,
      errorText: "Not authorised",
    });
  require("./license").requestLicense(message.payload, function (result) {
    message.respond(result);
  });
});
