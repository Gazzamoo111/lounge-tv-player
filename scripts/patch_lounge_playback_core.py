#!/usr/bin/env python3
"""Patch the exact GPLv3 OwnTV Core 1.0.69 source for every Lounge TV build.

Do not silently apply these edits to a moving upstream branch. CI, isolated QA and
signed release builds all invoke this same fail-closed patcher against the pinned commit.
"""
import subprocess
from pathlib import Path
import sys

PIN = "1de78b1e2690c22b50c060e986bce221454bf880"
PATCHES = {
  "player-core/src/main/java/tv/own/owntv/player/LivePreviewEngine.kt": [
    [
      "if (LiveStreamQuirks.isSessionLimit(code)) { noteSessionLimit(loadEventInfo.uri.toString()); return }",
      "if (LiveStreamQuirks.isSessionLimit(code) || LiveStreamQuirks.isTransientGatewayRefusal(code)) {\n                noteSessionLimit(loadEventInfo.uri.toString(), code); return\n            }"
    ],
    [
      "    private fun noteSessionLimit(uri: String) {\n        val url = currentUrl ?: return\n        LiveStreamQuirks.rememberSessionLimit(url)",
      "    private fun noteSessionLimit(uri: String, status: Int) {\n        val url = currentUrl ?: return\n        // HTTP 407 may come from a restarting gateway; it is not proof of a one-session account.\n        if (status == 458) LiveStreamQuirks.rememberSessionLimit(url)"
    ],
    [
      "            \"provider refused with HTTP 458 (account session still in use) uri=${HttpClient.redactUrl(uri)}\",",
      "            \"provider refused with HTTP $status uri=${HttpClient.redactUrl(uri)}\","
    ],
    [
      "            reason = PlayerFailureReason.ONE_SESSION_PROVIDER,\n            detail = \"HTTP 458 while the previous engine's session was still open — waiting and retrying once\",",
      "            reason = if (status == 458) PlayerFailureReason.ONE_SESSION_PROVIDER else PlayerFailureReason.NETWORK,\n            detail = \"HTTP 458 while the previous engine's session was still open — waiting and retrying once\","
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
      "                    val finalUrl = response.request.url.toString()\n                    // Some IPTV gateways redirect a failed live request to a finite, static black.ts.\n                    // Treat that placeholder as an upstream failure, never as a healthy TV channel.\n                    if (isBlackPlaceholderRedirect(request.url.toString(), finalUrl)) {\n                        response.close()\n                        throw java.io.IOException(\"Live provider returned a black placeholder instead of the channel\")\n                    }\n                    if (isHlsResponse(finalUrl, response.header(\"Content-Type\"))) {"
    ],
    [
      "    @Volatile private var currentDrm: tv.own.owntv.core.drm.DrmConfig? = null",
      "    @Volatile private var currentDrm: tv.own.owntv.core.drm.DrmConfig? = null\n    /** Secure and clear decoders must not share a stale ExoPlayer codec across a channel switch. */\n    private var previousTuneProtected: Boolean = false"
    ],
    [
      "        currentDrm = tv.own.owntv.core.drm.DrmConfig.decode(drmConfig)\n        currentManifestType =",
      "        currentDrm = tv.own.owntv.core.drm.DrmConfig.decode(drmConfig)\n        val protectionBoundary = crossesProtectionBoundary(previousTuneProtected, currentDrm != null)\n        previousTuneProtected = currentDrm != null\n        currentManifestType ="
    ],
    [
      "            if (player != null && (builtLoadControl != wanted || (memoryPressure && !builtLowRam) || builtTunneled != wantTunneling() ||\n                    builtHwDecoding != hwDecodingEnabled)) {",
      "            if (player != null && (protectionBoundary || builtLoadControl != wanted || (memoryPressure && !builtLowRam) || builtTunneled != wantTunneling() ||\n                    builtHwDecoding != hwDecodingEnabled)) {"
    ],
    [
      "                player?.run { removeListener(listener); release() }\n                player = null\n                videoRenderer = null\n            }\n            val p = player ?: build().also { player = it }",
      "                player?.run { removeListener(listener); release() }\n                player = null\n                videoRenderer = null\n                if (protectionBoundary) {\n                    // MediaTek secure codecs can inherit an unusable native window when switching\n                    // from plain TS to Widevine DASH. Recreate the SurfaceView as well as ExoPlayer.\n                    surface = null\n                    awaitingFreshSurface = true\n                    recreateSurface()\n                }\n            }\n            val p = player ?: build().also { player = it }"
    ],
    [
      "                if (++openStuckPolls >= OPEN_STUCK_POLLS) {\n                    logHlsPlaylist(\"open stalled\")",
      "                if (++openStuckPolls >= OPEN_STUCK_POLLS) {\n                    if (shouldRetryProtectedNoFrame(currentDrm != null, tune.decoderRetryDone)) {\n                        // DRM cannot fall back to mpv. If the secure decoder consumed buffered video\n                        // but rendered no frame, retry exactly once with fresh codec and surface.\n                        tune.decoderRetryDone = true\n                        LiveDiagnosticsLog.event(\"protected video buffered but no frame: resetting secure decoder and surface once\")\n                        rebuildDecoderAndSurface()\n                        return\n                    }\n                    logHlsPlaylist(\"open stalled\")"
    ],
    [
      "    var currentUrl: String? = null\n        private set\n\n    /**",
      "    var currentUrl: String? = null\n        private set\n    /** Monotonic tune identity; URL equality is insufficient when two channels share a fallback URL. */\n    private var tuneGeneration: Long = 0L\n\n    /**"
    ],
    [
      "    ) {\n        LiveDiagnosticsLog.event(\"play() engine=$engineId url=${HttpClient.redactUrl(url)} muted=$muted\")",
      "    ) {\n        tuneGeneration++\n        LiveDiagnosticsLog.event(\"play() engine=$engineId url=${HttpClient.redactUrl(url)} muted=$muted\")"
    ],
    [
      "        tune.stoppingIntentionally = true\n        currentUrl = null\n        tune.hasPlayed = false;",
      "        tune.stoppingIntentionally = true\n        tuneGeneration++\n        currentUrl = null\n        tune.hasPlayed = false;"
    ],
    [
      "        surface = null\n        currentUrl = null\n        deadlineArmed = false",
      "        surface = null\n        tuneGeneration++\n        currentUrl = null\n        deadlineArmed = false"
    ],
    [
      "        val p = player\n        val url = currentUrl\n        val nowMs = android.os.SystemClock.elapsedRealtime()",
      "        val p = player\n        val url = currentUrl\n        val generation = tuneGeneration\n        val nowMs = android.os.SystemClock.elapsedRealtime()"
    ],
    [
      "            // Coalesce the backoff delay with the resolve: whichever is later wins, but the resolve must\n            // complete before we reload. Post the reload so it lands on the main thread's Looper after delay.",
      "            if (tuneGeneration != generation || currentUrl != url) return@launch\n            // Coalesce the backoff delay with the resolve: whichever is later wins, but the resolve must\n            // complete before we reload. Post the reload so it lands on the main thread's Looper after delay."
    ],
    [
      "                if (currentUrl != url) { tune.reconnectPending = false; return@Runnable } // superseded (zapped / stopped)",
      "                if (tuneGeneration != generation || currentUrl != url) {\n                    tune.reconnectPending = false; return@Runnable\n                } // superseded (zapped / stopped), even if the replacement channel reuses the same URL"
    ],
    [
      "            else -> if (reason == \"ended mid-live\") maxOf(reconnectDelayMs(tune.retryCount), 3_500L)\n                    else reconnectDelayMs(tune.retryCount)",
      "            else -> reconnectDelayFor(reason, tune.retryCount)"
    ],
    [
      "        internal fun retryAfterSecs(header: String?): Int? =\n            header?.trim()?.toIntOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_SECS }?.coerceAtLeast(1)",
      "        internal fun retryAfterSecs(header: String?): Int? =\n            header?.trim()?.toIntOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_SECS }?.coerceAtLeast(1)\n\n        internal fun isBlackPlaceholderRedirect(requestedUrl: String, finalUrl: String): Boolean {\n            val requestedPath = requestedUrl.substringBefore('?')\n            val finalPath = finalUrl.substringBefore('?')\n            return !requestedPath.equals(finalPath, ignoreCase = true) &&\n                finalPath.substringAfterLast('/').equals(\"black.ts\", ignoreCase = true)\n        }\n\n        internal fun crossesProtectionBoundary(previousProtected: Boolean, nextProtected: Boolean): Boolean =\n            previousProtected != nextProtected\n\n        internal fun shouldRetryProtectedNoFrame(protected: Boolean, retryDone: Boolean): Boolean =\n            protected && !retryDone\n\n        internal fun reconnectDelayFor(reason: String, attempt: Int): Long =\n            if (reason == \"ended mid-live\") maxOf(reconnectDelayMs(attempt), 3_500L)\n            else reconnectDelayMs(attempt)"
    ]
  ],
  "player-core/src/main/java/tv/own/owntv/player/LiveTuneController.kt": [
    [
      "    fun preview(channel: ChannelEntity, muted: Boolean) {",
      "    /** Releasing the old preview socket before starting a different channel is essential on\n     *  one-stream Xtream accounts. Keeping the preview feature does not mean two connections\n     *  are permitted to overlap while the user changes focus. */\n    // Persist the cooldown across cancelled preview jobs. Without this, quickly moving focus\n    // A -> B -> C cancels A's delay, and C opens while A's socket is still closing.\n    private var previewSessionReleaseUntilMs = 0L\n\n    private suspend fun handoffPreviewSession(\n        channel: ChannelEntity, source: SourceEntity?, nextUrl: String,\n    ): Boolean {\n        val isSingleSession = singleSessionPanel(channel, source)\n        val previousUrl = engines.exoUrl\n        if (previousUrl != null && previousUrl != nextUrl && isSingleSession) {\n            engines.exoStop()\n            previewSessionReleaseUntilMs = host.nowMs() + LivePreviewEngine.SESSION_RELEASE_MS\n        }\n        // The cooldown belongs to the controller, not a cancellable focus job.\n        // Otherwise a new focus can skip the wait because exoStop() already cleared exoUrl.\n        if (isSingleSession) {\n            val remainingMs = previewSessionReleaseUntilMs - host.nowMs()\n            if (remainingMs > 0L) delay(remainingMs)\n        }\n        return !_liveOnExo.value && previewedChannelId == channel.id\n    }\n\n    fun preview(channel: ChannelEntity, muted: Boolean) {"
    ],
    [
      "                if (blockedBySingleSession(url, channel)) return@launch\n                exoStalkerCmd = channel.streamUrl",
      "                if (blockedBySingleSession(url, channel)) return@launch\n                if (!handoffPreviewSession(channel, source, url)) return@launch\n                exoStalkerCmd = channel.streamUrl"
    ],
    [
      "            exoStalkerCmd = null\n            setReconnect(null, null)\n            engines.exoPlay(url, muted, request(channel, source))",
      "            if (!handoffPreviewSession(channel, source, url)) return@launch\n            exoStalkerCmd = null\n            setReconnect(null, null)\n            engines.exoPlay(url, muted, request(channel, source))"
    ]
  ],
  "player-core/src/main/java/tv/own/owntv/player/LiveStreamQuirks.kt": [
    [
      "     * `407` joins it for the same reason. A panel fronted by a gateway answers the *reconnect* with\n     * \"Proxy Authentication Required\" while the dying connection is still counted against the\n     * account, even though no proxy is in play — traced on a television whose channel had played for\n     * twenty seconds with zero dropped frames, then burned all eight reconnects on `407` in six\n     * seconds and gave up. The stream was never the problem: the previous socket had not finished\n     * closing. Waiting once is the answer here exactly as it is for `458`.\n",
      "     * A `407` observed from a proxyless IPTV gateway is deliberately handled separately. It can be\n     * transient while an upstream route or old socket is being released, but unlike `458` it is not\n     * durable evidence that the customer's account has a one-session limit.\n"
    ],
    [
      "    fun isSessionLimit(responseCode: Int): Boolean =\n        responseCode == 458 || responseCode == PlayerErrors.HTTP_PROXY_AUTH",
      "    fun isSessionLimit(responseCode: Int): Boolean = responseCode == 458\n\n    /** A proxyless 407 is a transient gateway restart signal, not proof of an account limit. */\n    fun isTransientGatewayRefusal(responseCode: Int): Boolean =\n        responseCode == PlayerErrors.HTTP_PROXY_AUTH"
    ],
    [
      "            responseCode == 503 || responseCode == 509 || isSessionLimit(responseCode)",
      "            responseCode == 503 || responseCode == 509 || isSessionLimit(responseCode) ||\n            isTransientGatewayRefusal(responseCode)"
    ]
  ],
  "player-core/src/main/java/tv/own/owntv/player/OwnTVPlayer.kt": [
    [
      "code == 408 || code == 429 || LiveStreamQuirks.isSessionLimit(code) -> HttpRefusal.BUSY",
      "code == 408 || code == 429 || LiveStreamQuirks.isSessionLimit(code) ||\n                    LiveStreamQuirks.isTransientGatewayRefusal(code) -> HttpRefusal.BUSY"
    ]
  ],
  "player-core/src/main/java/tv/own/owntv/player/PlayerDiagnostics.kt": [
    [
      "            httpCode == \"429\" || httpCode == \"458\" -> PlayerFailureReason.ONE_SESSION_PROVIDER\n            httpCode == \"403\" -> PlayerFailureReason.HTTP_403",
      "            httpCode == \"429\" || httpCode == \"458\" -> PlayerFailureReason.ONE_SESSION_PROVIDER\n            httpCode == \"407\" -> PlayerFailureReason.NETWORK\n            httpCode == \"403\" -> PlayerFailureReason.HTTP_403"
    ]
  ],
  "player-core/src/test/java/tv/own/owntv/player/LiveStreamQuirksTest.kt": [
    [
      "        assertTrue(LiveStreamQuirks.isSessionLimit(458))\n        assertFalse(LiveStreamQuirks.isSessionLimit(403))",
      "        assertTrue(LiveStreamQuirks.isSessionLimit(458))\n        assertFalse(LiveStreamQuirks.isSessionLimit(407))\n        assertTrue(LiveStreamQuirks.isTransientGatewayRefusal(407))\n        assertFalse(LiveStreamQuirks.isTransientGatewayRefusal(458))\n        assertFalse(LiveStreamQuirks.isSessionLimit(403))"
    ]
  ],
  "player-core/src/test/java/tv/own/owntv/player/LiveReconnectLadderTest.kt": [
    [
      "        assertEquals(OwnTVPlayer.HttpRefusal.BUSY, OwnTVPlayer.httpRefusalKind(\"ffmpeg: http: HTTP error 458 <none>\"))",
      "        assertEquals(OwnTVPlayer.HttpRefusal.BUSY, OwnTVPlayer.httpRefusalKind(\"ffmpeg: http: HTTP error 458 <none>\"))\n        assertEquals(OwnTVPlayer.HttpRefusal.BUSY, OwnTVPlayer.httpRefusalKind(\"ffmpeg: http: HTTP error 407 <none>\"))"
    ],
    [
      "    @Test\n    fun `an HTTP refusal stops the identical-request retries but leaves the fallbacks armed`() {",
      "    @Test\n    fun `a redirect to black ts is a failure and never a successful channel`() {\n        assertTrue(LivePreviewEngine.isBlackPlaceholderRedirect(\n            \"http://panel/live/502.ts?token=old\",\n            \"http://panel/video/black.ts?reason=offline\",\n        ))\n        assertTrue(LivePreviewEngine.isBlackPlaceholderRedirect(\n            \"http://panel/live/1.ts\", \"http://cdn/BLACK.TS\",\n        ))\n        assertFalse(LivePreviewEngine.isBlackPlaceholderRedirect(\n            \"http://panel/video/black.ts?token=old\",\n            \"http://panel/video/black.ts?token=new\",\n        ))\n        assertFalse(LivePreviewEngine.isBlackPlaceholderRedirect(\n            \"http://panel/live/502.ts\", \"http://cdn/live/502.ts\",\n        ))\n    }\n\n    @Test\n    fun `raw TS ending mid live waits for socket release before reconnecting`() {\n        assertEquals(3_500L, LivePreviewEngine.reconnectDelayFor(\"ended mid-live\", 1))\n        assertEquals(6_000L, LivePreviewEngine.reconnectDelayFor(\"ended mid-live\", 3))\n        assertEquals(1_500L, LivePreviewEngine.reconnectDelayFor(\"idle mid-live\", 1))\n    }\n\n    @Test\n    fun `DRM transitions rebuild codec and protected no-frame retries only once`() {\n        assertTrue(LivePreviewEngine.crossesProtectionBoundary(false, true))\n        assertTrue(LivePreviewEngine.crossesProtectionBoundary(true, false))\n        assertFalse(LivePreviewEngine.crossesProtectionBoundary(true, true))\n        assertTrue(LivePreviewEngine.shouldRetryProtectedNoFrame(true, false))\n        assertFalse(LivePreviewEngine.shouldRetryProtectedNoFrame(true, true))\n        assertFalse(LivePreviewEngine.shouldRetryProtectedNoFrame(false, false))\n    }\n\n    @Test\n    fun `407 is network recovery while real 458 remains a session limit`() {\n        assertEquals(PlayerFailureReason.NETWORK, PlayerErrors.classify(\"HTTP error 407\"))\n        assertEquals(PlayerFailureReason.ONE_SESSION_PROVIDER, PlayerErrors.classify(\"HTTP error 458\"))\n    }\n\n    @Test\n    fun `an HTTP refusal stops the identical-request retries but leaves the fallbacks armed`() {"
    ]
  ],
  "player-core/src/test/java/tv/own/owntv/player/LiveTuneControllerTest.kt": [
    [
      "        override fun exoStop() { exoUrl = null }",
      "        override fun exoStop() { if (exoUrl != null) order += \"exo-stop\"; exoUrl = null }"
    ],
    [
      "    @Test\n    fun `on other panels the handover from mpv keeps its asynchronous stop`() = runTest {\n        LiveStreamQuirks.clearForTest()\n        val engines = FakeEngines().apply { mpvHasStream = true }\n        val c = controller(engines, FakeHost(this))\n        c.tune(channel(21))\n        runCurrent()\n        assertEquals(listOf(\"mpv-stop\", \"exo-open\"), engines.order)\n    }\n}",
      "    @Test\n    fun `on other panels the handover from mpv keeps its asynchronous stop`() = runTest {\n        LiveStreamQuirks.clearForTest()\n        val engines = FakeEngines().apply { mpvHasStream = true }\n        val c = controller(engines, FakeHost(this))\n        c.tune(channel(21))\n        runCurrent()\n        assertEquals(listOf(\"mpv-stop\", \"exo-open\"), engines.order)\n    }\n\n    @Test\n    fun `thirty rapid preview switches on one-session provider open only first and final channels`() = runTest {\n        LiveStreamQuirks.clearForTest()\n        try {\n            LiveStreamQuirks.rememberSessionLimit(channel(30).streamUrl)\n            val engines = FakeEngines()\n            val c = controller(engines, FakeHost(this))\n            c.preview(channel(30), muted = true)\n            runCurrent()\n            repeat(29) { index ->\n                c.preview(channel(31L + index), muted = true)\n                runCurrent()\n            }\n            assertEquals(1, engines.log.count { it.startsWith(\"exo:\") })\n            advanceTimeBy(LivePreviewEngine.SESSION_RELEASE_MS + 1)\n            runCurrent()\n            assertEquals(2, engines.log.count { it.startsWith(\"exo:\") })\n            assertEquals(\"exo:${channel(59).streamUrl}\", engines.log.last())\n            assertEquals(listOf(\"exo-open\", \"exo-stop\", \"exo-open\"), engines.order)\n        } finally {\n            LiveStreamQuirks.clearForTest()\n        }\n    }\n\n    @Test\n    fun `preview promotes to fullscreen and returns to muted preview without reopening`() = runTest {\n        val engines = FakeEngines()\n        val c = controller(engines, FakeHost(this))\n        c.preview(channel(90), muted = true)\n        runCurrent()\n        c.tune(channel(90))\n        runCurrent()\n        c.detach()\n        c.preview(channel(90), muted = true)\n        runCurrent()\n        assertEquals(1, engines.log.count { it.startsWith(\"exo:\") })\n        assertEquals(\"exo-mute\", engines.log.last())\n    }\n}\n"
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
        path.write_text(patched.rstrip() + "\n", encoding="utf-8")
        print(f"Patched {rel_path} ({len(changes)} exact replacements)")

    # OwnTV's translation inventory is a release gate. Review and classify the exact
    # new protocol/diagnostic literals rather than bypassing the checker.
    relative = "player-core/src/main/java/tv/own/owntv/player/LivePreviewEngine.kt"
    manifest = root / "tools/i18n/safe_literals.txt"
    rows = manifest.read_text(encoding="utf-8").splitlines()
    old_entries = [
        "1\tlog\t" + relative + "\tHTTP 458 while the previous engine's session was still open — waiting and retrying once",
        "1\ttechnical\t" + relative + "\tprovider refused with HTTP 458 (account session still in use) uri=${HttpClient.redactUrl(uri)}",
    ]
    for entry in old_entries:
        if rows.count(entry) != 1:
            raise SystemExit(f"Safe literal entry missing or ambiguous: {entry}")
        rows.remove(entry)
    old_ended = "1\ttechnical\t" + relative + "\tended mid-live"
    if rows.count(old_ended) != 1:
        raise SystemExit("Expected one existing ended-mid-live safe literal")
    rows[rows.index(old_ended)] = "2\ttechnical\t" + relative + "\tended mid-live"

    new_literals = [
        ("1", "file-path", "black.ts"),
        ("1", "log", "HTTP $status while opening a live stream — waiting for the gateway or previous session to release, then retrying"),
        ("1", "log", "Live provider returned a black placeholder instead of the channel"),
        ("1", "log", "protected video buffered but no frame: resetting secure decoder and surface once"),
        ("1", "log", "provider refused with HTTP $status uri=${HttpClient.redactUrl(uri)}"),
    ]
    if not rows or "" not in rows:
        raise SystemExit("Unrecognized upstream safe literal manifest")
    header_end = rows.index("") + 1
    for count, category, literal in new_literals:
        entry = "\t".join((count, category, relative, literal))
        if entry in rows:
            raise SystemExit(f"Unexpected existing safe literal: {literal}")
        rows.append(entry)
    diagnostics_407 = "1\ttechnical\tplayer-core/src/main/java/tv/own/owntv/player/PlayerDiagnostics.kt\t407"
    if diagnostics_407 in rows:
        raise SystemExit("Unexpected existing PlayerDiagnostics 407 safe literal")
    rows.append(diagnostics_407)
    body = sorted(rows[header_end:], key=lambda row: (row.split("\t", 3)[2], row.split("\t", 3)[3]))
    manifest.write_text("\n".join(rows[:header_end] + body) + "\n", encoding="utf-8")
    print("Reviewed protocol and log literals in pinned-core i18n inventory")
    reliability_patch = Path(__file__).resolve().parent / "patches/playback-reliability.patch"
    subprocess.run(["git", "-C", str(root), "apply", "--check", str(reliability_patch)], check=True)
    subprocess.run(["git", "-C", str(root), "apply", str(reliability_patch)], check=True)
    print("Applied reviewed tune-cancellation and credential backport")
    print(f"Playback core patch applied to pinned commit {PIN}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_lounge_playback_core.py path/to/OwnTV_Core")
    apply(Path(sys.argv[1]).resolve())
