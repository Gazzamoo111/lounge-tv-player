"use strict";
var https = require("https"),
  url = require("url");
var UA =
  "otg/1.5.1 (AppleTv Apple TV 4; tvOS16.0; appletv.client) libcurl/7.58.0 OpenSSL/1.0.2o zlib/1.2.11 clib/1.8.56";
function allowed(target) {
  var u = url.parse(target);
  if (u.protocol !== "https:" || u.auth || (u.port && u.port !== "443"))
    return false;
  return (
    (u.hostname === "c.mjh.nz" && /^\/tvnz-[12]-wv$/.test(u.pathname)) ||
    (u.hostname === "fvnz-capi-prod.switch.tv" &&
      u.pathname === "/playback-api/getlicense") ||
    (u.hostname === "apis-public-prod.tvnz.io" &&
      u.pathname === "/license/v1/wv")
  );
}
function requestLicense(payload, callback, transport) {
  if (
    !payload ||
    !/^tvnz-[12]$/.test(payload.channel) ||
    typeof payload.challenge !== "string" ||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(payload.challenge) ||
    payload.challenge.length > 350000
  ) {
    callback({
      returnValue: false,
      errorCode: 9030,
      errorText: "Invalid license request",
    });
    return function () {};
  }
  var body = Buffer.from
    ? Buffer.from(payload.challenge, "base64")
    : new Buffer(payload.challenge, "base64");
  if (!body.length || body.length > 262144) {
    callback({
      returnValue: false,
      errorCode: 9030,
      errorText: "Invalid license request",
    });
    return function () {};
  }
  var ended = false,
    active,
    deadline;
  function finish(result) {
    if (ended) return;
    ended = true;
    clearTimeout(deadline);
    callback(result);
  }
  function cancel() {
    if (ended) return;
    ended = true;
    clearTimeout(deadline);
    if (active) active.destroy();
  }
  deadline = setTimeout(function () {
    if (active) active.destroy();
    finish({
      returnValue: false,
      errorCode: 6007,
      errorText: "License request timed out",
    });
  }, 12000);
  function send(target, count) {
    if (!allowed(target) || count > 3)
      return finish({
        returnValue: false,
        errorCode: 9030,
        errorText: "Unsupported license redirect",
      });
    var u = url.parse(target),
      options = {
        protocol: u.protocol,
        hostname: u.hostname,
        port: 443,
        path: u.path,
        method: "POST",
        headers: {
          "Content-Type": "application/octet-stream",
          Accept: "application/octet-stream",
          "Content-Length": body.length,
          "User-Agent": UA,
        },
      };
    active = (transport || https.request)(options, function (response) {
      if (ended) {
        response.resume();
        return;
      }
      var status = response.statusCode;
      if (
        [301, 302, 303, 307, 308].indexOf(status) >= 0 &&
        response.headers.location
      ) {
        response.resume();
        send(url.resolve(target, response.headers.location), count + 1);
        return;
      }
      if (status < 200 || status >= 300) {
        response.resume();
        return finish({
          returnValue: false,
          errorCode: 6007,
          httpStatus: status,
          errorText: "License server refused the request",
        });
      }
      var chunks = [],
        size = 0;
      response.on("data", function (part) {
        if (ended) return;
        size += part.length;
        if (size > 1048576) {
          response.destroy();
          finish({
            returnValue: false,
            errorCode: 6007,
            errorText: "Invalid license response",
          });
        } else chunks.push(part);
      });
      response.on("error", function () {
        finish({
          returnValue: false,
          errorCode: 6007,
          errorText: "License response interrupted",
        });
      });
      response.on("end", function () {
        if (ended) return;
        var data = Buffer.concat(chunks);
        if (
          !data.length ||
          /text\/html|application\/json/.test(
            response.headers["content-type"] || "",
          )
        )
          return finish({
            returnValue: false,
            errorCode: 6007,
            errorText: "Invalid license response",
          });
        finish({
          returnValue: true,
          data: data.toString("base64"),
          httpStatus: status,
        });
      });
    });
    active.on("error", function () {
      finish({
        returnValue: false,
        errorCode: 6007,
        errorText: "License connection failed",
      });
    });
    active.end(body);
  }
  send("https://c.mjh.nz/" + payload.channel + "-wv", 0);
  return cancel;
}
module.exports = { requestLicense: requestLicense, allowed: allowed };
