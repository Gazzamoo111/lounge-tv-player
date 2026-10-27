#!/usr/bin/env python3
"""Patch the exact GPLv3 OwnTV Core 1.0.69 source for Lounge TV's isolated QA build.

Do not silently apply these edits to a moving upstream branch. Production remains
unchanged until tests and real-device playback acceptance are complete.
"""
import subprocess
from pathlib import Path
import sys

PIN = "1de78b1e2690c22b50c060e986bce221454bf880"
PATCHES = {
  "player-core/src/main/java/tv/own/owntv/player/LivePreviewEngine.kt": [
    [
      "if (LiveStreamQuirks.isSessionLimit(code)) { noteSessionLimit(loadEventInfo.uri.toString()); return }",
      "if (LiveStreamQuirks.isSessionLimit(code)) { noteSessionLimit(loadEventInfo.uri.toString(), code); return }"
    ],
    [
      "    private fun noteSessionLimit(uri: String) {\n        val url = currentUrl ?: return\n        LiveStreamQuirks.rememberSessionLimit(url)",
      "    private fun noteSessionLimit(uri: String, status: Int) {\n        val url = currentUrl ?: return\n        // HTTP 407 may come from a restarting gateway; it is not proof of a one-session account.\n        if (status == 458) LiveStreamQuirks.rememberSessionLimit(url)"
    ],
    [
      "            \"provider refused with HTTP 458 (account session still in use) uri=${HttpClient.redactUrl(uri)}\",",
      "            \"provider refused with HTTP $status (${if (status == 458) \"account session limit\" else \"gateway/session retry required\"}) uri=${HttpClient.redactUrl(uri)}\","
    ],
    [
      "            detail = \"HTTP 458 while the previous engine's session was still open — waiting and retrying once\",",
      "            detail = \"HTTP $status while opening a live stream — waiting for the gateway or previous session to release, then retrying\","
    ],
    [
      "if (tune.hasPlayed && tune.reconnectPending && !tune.gaveUp && isAnswerAfterOutage(error)) {",
      "if (tune.hasPlayed && tune.reconnectPending && !tune.gaveUp && isAnswerAfterOutage(error) &&\n                httpStatusOf(error) != PlayerErrors.HTTP_PROXY_AUTH) {"
    ],
    [
      "            else -> reconnectDelayMs(tune.retryCount)\n        }\n        // Resolve a fresh URL off-main",
      "            // An ended raw-TS feed can still occupy its provider session while the socket closes.\n            // Avoid opening the new connection immediately and receiving a chain of 407s.\n            else -> if (reason == \"ended mid-live\") maxOf(reconnectDelayMs(tune.retryCount), 3_500L)\n                    else reconnectDelayMs(tune.retryCount)\n        }\n        // Resolve a fresh URL off-main"
    ],
    [
      "                    val finalUrl = response.request.url.toString()\n                    if (isHlsResponse(finalUrl, response.header(\"Content-Type\"))) {",
      "                    val finalUrl = response.request.url.toString()\n                    // Some IPTV gateways redirect a failed live request to a finite, static black.ts.\n                    // Treat that placeholder as an upstream failure, never as a healthy TV channel.\n                    if (finalUrl.substringBefore('?').endsWith(\"/video/black.ts\", ignoreCase = true) &&\n                        !request.url.toString().substringBefore('?').endsWith(\"/video/black.ts\", ignoreCase = true)) {\n                        response.close()\n                        throw java.io.IOException(\"Live provider returned a black placeholder instead of the channel\")\n                    }\n                    if (isHlsResponse(finalUrl, response.header(\"Content-Type\"))) {"
    ],
    [
      "    @Volatile private var currentDrm: tv.own.owntv.core.drm.DrmConfig? = null",
      "    @Volatile private var currentDrm: tv.own.owntv.core.drm.DrmConfig? = null\n    /** Secure and clear decoders must not share a stale ExoPlayer codec across a channel switch. */\n    private var previousTuneProtected: Boolean = false"
    ],
    [
      "        currentDrm = tv.own.owntv.core.drm.DrmConfig.decode(drmConfig)\n        currentManifestType =",
      "        currentDrm = tv.own.owntv.core.drm.DrmConfig.decode(drmConfig)\n        val protectionBoundary = (currentDrm != null) != previousTuneProtected\n        previousTuneProtected = currentDrm != null\n        currentManifestType ="
    ],
    [
      "            if (player != null && (builtLoadControl != wanted || (memoryPressure && !builtLowRam) || builtTunneled != wantTunneling() ||\n                    builtHwDecoding != hwDecodingEnabled)) {",
      "            if (player != null && (protectionBoundary || builtLoadControl != wanted || (memoryPressure && !builtLowRam) || builtTunneled != wantTunneling() ||\n                    builtHwDecoding != hwDecodingEnabled)) {"
    ]
  ]
}

def apply(root: Path) -> None:
    actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if actual != PIN:
        raise SystemExit(f"Core revision mismatch: expected {PIN}, got {actual}")
    for rel_path, changes in PATCHES.items():
        path = root / rel_path
        original = path.read_text(encoding="utf-8")
        patched = original
        for number, (before, after) in enumerate(changes, 1):
            occurrences = patched.count(before)
            if occurrences != 1:
                raise SystemExit(f"Refusing ambiguous patch {rel_path} #{number}: matches={occurrences}")
            patched = patched.replace(before, after, 1)
        path.write_text(patched, encoding="utf-8")
        print(f"Patched {rel_path} ({len(changes)} exact replacements)")
    print(f"Playback core patch applied to pinned commit {PIN}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_lounge_playback_core.py path/to/OwnTV_Core")
    apply(Path(sys.argv[1]).resolve())
