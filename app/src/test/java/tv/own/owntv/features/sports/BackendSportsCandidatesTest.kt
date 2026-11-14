package tv.own.owntv.features.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.Collections
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Sport
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.sports.WatchFilter
import tv.own.owntv.core.sports.WatchList
import tv.own.owntv.core.sports.WatchOption
import tv.own.owntv.core.sports.WatchTier

class BackendSportsCandidatesTest {
    private fun fixture(sport: Sport) = Match("qa-fixture", sport, "test/competition", "competition", "TEST competition",
        1_794_124_800_000L, MatchState.PRE, "TEST scheduled", "TEST fixture")
    private fun source(username: String = "qa-user", password: String = "qa-pass") = SourceEntity(
        id = 7, name = "TEST source", type = SourceType.XTREAM, url = "https://source.invalid",
        username = username, password = password,
    )
    private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split("&").filter { it.isNotEmpty() }.associate {
        val pair = it.split("=", limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }
    private suspend fun withCandidateServer(body: String, run: suspend (String, MutableList<URI>) -> Unit) {
        val requests = Collections.synchronizedList(mutableListOf<URI>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestURI
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { run("http://127.0.0.1:${server.address.port}", requests) } finally { server.stop(0) }
    }

    @Test fun `QA tennis discovery produces isolated QA gateway URLs and plausible evidence`() = runBlocking {
        withCandidateServer("""{"candidates":[{"providerItemId":1881735,"displayName":":Tennis 30","confidence":"definite"}]}""") { base, requests ->
            val match = fixture(Sport.TENNIS)
            val provider = BackendSportsCandidateProvider(endpoint = "$base/production-candidates", qaEndpoint = "$base/qa-candidates", loungeTv2 = true)
            val options = provider.options(match, source())
            assertEquals(1, options.size)
            assertEquals("/qa-candidates", requests.single().path)
            val request = query(requests.single())
            assertEquals("2", request["contractVersion"])
            assertEquals(match.key, request["eventId"])
            assertEquals("TENNIS", request["sportId"])
            assertEquals("competition", request["competitionId"])
            assertFalse(request.containsKey("username"))
            assertFalse(request.containsKey("password"))
            val url = URI(options.single().channel.streamUrl)
            assertEquals("/functions/v1/lounge-core-xtream-qa-next/sport/_/_/1881735.ts", url.path)
            assertEquals("liqocisegeqzbyuqloql.supabase.co", url.host)
            assertEquals(WatchTier.LIKELY, options.single().tier)
            assertEquals(BACKEND_CONFIDENCE_PREFIX + "maybe", options.single().guideTitle)
        }
    }

    @Test fun `QA non-tennis preserves production candidate contract and playback gateway`() = runBlocking {
        withCandidateServer("""{"candidates":[{"providerItemId":701,"name":"Sky Sport 1","confidence":"likely"}]}""") { base, requests ->
            val provider = BackendSportsCandidateProvider(endpoint = "$base/production-candidates", qaEndpoint = "$base/qa-candidates", loungeTv2 = true)
            val options = provider.options(fixture(Sport.SOCCER), source())
            assertEquals("/production-candidates", requests.single().path)
            assertEquals(Sport.SOCCER.path, query(requests.single())["sport"])
            assertFalse(query(requests.single()).containsKey("contractVersion"))
            assertEquals("/functions/v1/lounge-core-xtream/sport/_/_/701.ts", URI(options.single().channel.streamUrl).path)
            assertEquals(WatchTier.EVENT, options.single().tier)
        }
    }

    @Test fun `production tennis preserves legacy discovery playback and evidence contracts`() = runBlocking {
        withCandidateServer("""{"candidates":[{"providerItemId":701,"name":"Tennis Channel","confidence":"definite"}]}""") { base, requests ->
            val provider = BackendSportsCandidateProvider(endpoint = "$base/production-candidates", qaEndpoint = "$base/qa-candidates", loungeTv2 = false)
            val options = provider.options(fixture(Sport.TENNIS), source())
            assertEquals("/production-candidates", requests.single().path)
            assertFalse(query(requests.single()).containsKey("contractVersion"))
            assertEquals("/functions/v1/lounge-core-xtream/sport/_/_/701.ts", URI(options.single().channel.streamUrl).path)
            assertEquals(WatchTier.GUIDE, options.single().tier)
            assertEquals(BACKEND_CONFIDENCE_PREFIX + "definite", options.single().guideTitle)
        }
    }

    @Test fun `QA tennis playback query preserves encoded credentials without leaking them into discovery`() = runBlocking {
        withCandidateServer("""{"candidates":[{"providerItemId":701,"displayName":"Tennis Channel"}]}""") { base, requests ->
            val credentials = source("qa +&=?/ü", "test&pass=+/ ?")
            val provider = BackendSportsCandidateProvider(endpoint = "$base/production-candidates", qaEndpoint = "$base/qa-candidates", loungeTv2 = true)
            val channel = provider.options(fixture(Sport.TENNIS), credentials).single().channel
            val url = URI(channel.streamUrl)
            assertEquals(credentials.username, query(url)["username"])
            assertEquals(credentials.password, query(url)["password"])
            assertEquals(2, query(url).size)
            assertTrue(url.rawQuery.contains("%26"))
            assertTrue(url.rawQuery.contains("%2B"))
            assertFalse(requests.single().toString().contains("password"))
            assertEquals(credentials.id, channel.sourceId)
        }
    }

    @Test fun `QA tennis rejects retrospective or malformed suggestions and missing credentials before network`() = runBlocking {
        withCandidateServer("""{"candidates":[
            {"providerItemId":701,"displayName":"Tennis Replay"},
            {"providerItemId":702,"displayName":"Tennis Highlights"},
            {"providerItemId":0,"displayName":"Tennis invalid"},
            {"providerItemId":703,"displayName":""},
            {"providerItemId":704,"displayName":"Tennis Channel","confidence":"definite"}]}""") { base, requests ->
            val provider = BackendSportsCandidateProvider(endpoint = "$base/production-candidates", qaEndpoint = "$base/qa-candidates", loungeTv2 = true)
            val options = provider.options(fixture(Sport.TENNIS), source())
            assertEquals(listOf("Tennis Channel"), options.map { it.channel.name })
            assertEquals(WatchTier.LIKELY, options.single().tier)
            assertTrue(provider.options(fixture(Sport.TENNIS), source().copy(username = null)).isEmpty())
            assertTrue(provider.options(fixture(Sport.TENNIS), source(password = " ")).isEmpty())
            assertEquals(1, requests.size)
        }
    }

    private fun option(id: Long, name: String, tier: WatchTier, marker: String? = null) =
        WatchOption(
            channel = ChannelEntity(id = id, sourceId = 1L, name = name, streamUrl = "https://example.invalid/" + id),
            tier = tier,
            guideTitle = marker,
        )

    @Test
    fun `backend confidence order wins and weak local guesses are dropped`() {
        val backend = listOf(
            option(-1, "Exact event", WatchTier.GUIDE, BACKEND_CONFIDENCE_PREFIX + "definite"),
            option(-2, "Likely broadcaster", WatchTier.EVENT, BACKEND_CONFIDENCE_PREFIX + "likely"),
            option(-3, "Maybe network", WatchTier.LIKELY, BACKEND_CONFIDENCE_PREFIX + "maybe"),
        )
        val local = listOf(
            option(1, "Guide confirmed", WatchTier.GUIDE),
            option(2, "Music Choice Jazz", WatchTier.LIKELY),
        )

        val merged = mergeBackendSportsOptions(backend, local)
        val shown = WatchList.of(merged, WatchFilter()).shown

        assertEquals(listOf("Exact event", "Guide confirmed", "Likely broadcaster", "Maybe network"), shown.map { it.channel.name })
        assertFalse(shown.any { it.channel.name == "Music Choice Jazz" })
    }

    @Test
    fun `local results remain intact when backend is unavailable`() {
        val local = listOf(option(1, "NBA TV", WatchTier.LIKELY))
        assertEquals(local, mergeBackendSportsOptions(emptyList(), local))
    }
}
