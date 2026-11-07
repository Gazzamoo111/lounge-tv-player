"use strict";
var http = require("http"),
  crypto = require("crypto"),
  os = require("os"),
  fs = require("fs"),
  path = require("path");
function localAddress() {
  var interfaces = os.networkInterfaces(),
    rows = [];
  Object.keys(interfaces).forEach(function (key) {
    interfaces[key].forEach(function (r) {
      if (
        (r.family === "IPv4" || r.family === 4) &&
        !r.internal &&
        !/^169\.254\./.test(r.address)
      )
        rows.push(r.address);
    });
  });
  return (
    rows.filter(function (ip) {
      return /^192\.168\./.test(ip);
    })[0] || rows[0]
  );
}
function createCompanion(options) {
  options = options || {};
  var session,
    server,
    expiryTimer,
    starting = false;
  function reset() {
    session = undefined;
    clearTimeout(expiryTimer);
  }
  function stop() {
    reset();
    if (server) server.close();
    server = undefined;
  }
  function valid(token) {
    return (
      !!session &&
      session.expires > Date.now() &&
      typeof token === "string" &&
      token === session.token
    );
  }
  function reply(res, status, data, type) {
    res.writeHead(status, {
      "Content-Type": type || "application/json",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      "Referrer-Policy": "no-referrer",
      "Content-Security-Policy":
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; form-action 'none'; frame-ancestors 'none'",
    });
    res.end(
      typeof data === "string" || Buffer.isBuffer(data)
        ? data
        : JSON.stringify(data),
    );
  }
  function handle(req, res) {
    req.setEncoding("utf8");
    req.setTimeout(10000, function () {
      req.destroy();
    });
    var route = (req.url || "").split("?")[0];
    var host = req.headers.host || "";
    if (!/^((?:\d{1,3}\.){3}\d{1,3}|localhost):\d+$/.test(host))
      return reply(res, 403, { error: "Invalid host" });
    if (req.headers.origin && req.headers.origin !== "http://" + host)
      return reply(res, 403, { error: "Invalid origin" });
    if (!session || session.expires <= Date.now())
      return reply(res, 410, {
        error: "Code expired. Refresh the QR code on your TV.",
      });
    if (req.method === "GET" && route === "/")
      return reply(
        res,
        200,
        fs.readFileSync(path.join(__dirname, "phone.html")),
        "text/html; charset=utf-8",
      );
    if (req.method === "GET" && route === "/phone.js")
      return reply(
        res,
        200,
        fs.readFileSync(path.join(__dirname, "phone.js")),
        "application/javascript",
      );
    if (req.method === "POST" && route === "/sign-in") {
      var body = "",
        oversized = false;
      req.on("data", function (part) {
        body += part;
        if (body.length > 4096 && !oversized) {
          oversized = true;
          reply(res, 413, { error: "Details are too long" });
        }
      });
      req.on("end", function () {
        if (oversized) return;
        var value;
        try {
          value = JSON.parse(body);
        } catch (e) {
          return reply(res, 400, { error: "Invalid details" });
        }
        if (!valid(value.token))
          return reply(res, 403, {
            error: "Code expired. Refresh the QR code on your TV.",
          });
        if (session.credentials)
          return reply(res, 409, {
            error: "Details already sent. Check your TV.",
          });
        if (
          typeof value.username !== "string" ||
          !value.username.trim() ||
          value.username.length > 256 ||
          typeof value.password !== "string" ||
          !value.password ||
          value.password.length > 512
        )
          return reply(res, 400, {
            error: "Enter your Lounge TV username and password.",
          });
        session.credentials = {
          username: value.username.trim(),
          password: value.password,
        };
        return reply(res, 200, { sent: true });
      });
      req.on("error", function () {});
      return;
    }
    reply(res, 404, { error: "Not found" });
  }
  return {
    start: function (callback) {
      if (starting)
        return callback(new Error("Phone sign-in is starting. Try again."));
      stop();
      var ip = options.address || localAddress();
      if (!ip)
        return callback(
          new Error("Connect your TV to Wi-Fi or Ethernet first."),
        );
      starting = true;
      session = {
        token: crypto.randomBytes(32).toString("hex"),
        expires: Date.now() + 600000,
      };
      server = http.createServer(handle);
      server.on("error", function () {
        starting = false;
        stop();
        callback(
          new Error(
            "Phone sign-in could not start. Use the remote or try again.",
          ),
        );
      });
      server.listen(options.port || 0, options.bind || "0.0.0.0", function () {
        starting = false;
        var port = server.address().port;
        expiryTimer = setTimeout(stop, 600000);
        callback(null, {
          returnValue: true,
          token: session.token,
          url: "http://" + ip + ":" + port + "/#" + session.token,
          expires: session.expires,
        });
      });
    },
    take: function (token) {
      if (!valid(token))
        return { returnValue: false, errorText: "Phone sign-in expired." };
      if (!session.credentials) return { returnValue: true, pending: true };
      var credentials = session.credentials;
      stop();
      return { returnValue: true, credentials: credentials };
    },
    stop: function (token) {
      if (valid(token)) stop();
      return { returnValue: true };
    },
    close: stop,
  };
}
module.exports = { createCompanion: createCompanion };
