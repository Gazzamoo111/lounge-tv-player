package tv.own.owntv.features.sports

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sports.WatchTier
import tv.own.owntv.core.sports.model.*

class BackendSportsHttpTest {
    private val now = System.currentTimeMillis()
    private val match = Match("http-regression", Sport.SOCCER, "soccer/uefa.champions", "uefa.champions", "UEFA Champions League", now - 60_000, MatchState.IN, "IN", "Liverpool v Real Madrid")
    private val source = SourceEntity(id=1, name="QA", type=SourceType.XTREAM, url="https://example.invalid", username="test", password="test")
    private fun row(id: Long = 646977) = JSONObject().apply {
        put("providerItemId",id);put("displayName","AU| BEIN SPORTS 1 HD");put("sourceId","best")
        put("serviceMarket","AU");put("logicalServiceId","AU:BEIN:1");put("identityReviewed",true);put("tier","verified")
        put("expiresAtUtc",Instant.ofEpochMilli(now+120_000).toString())
        put("playbackObservation",JSONObject().apply {
            put("status","verified");put("httpAccessible",true);put("decodedFrames",1)
            put("providerItemId",id.toString());put("serviceId","AU:BEIN:1")
            put("observedAtUtc",Instant.ofEpochMilli(now).toString());put("streamFingerprint",java.security.MessageDigest.getInstance("SHA-256")
                .digest("liqocisegeqzbyuqloql.supabase.co|$id|AU:BEIN:1".toByteArray())
                .joinToString("") { "%02x".format(it) })
        })
        put("evidence",JSONArray().put(JSONObject().apply {
            put("type","programme");put("source","regression-only");put("eventId",match.key)
            put("sportId",match.sport.path);put("competitionId",match.feed);put("serviceId","AU:BEIN:1");put("market","AU")
            put("observedAtUtc",Instant.ofEpochMilli(now).toString());put("startUtc",Instant.ofEpochMilli(now-120_000).toString())
            put("endUtc",Instant.ofEpochMilli(now+120_000).toString())
        }))
    }
    private fun body(rows: JSONArray = JSONArray().put(row())) = JSONObject().apply {
        put("implementationId",QA_SPORTS_IMPLEMENTATION);put("contractVersion",2);put("eventId",match.key)
        put("expiresAtUtc",Instant.ofEpochMilli(now+120_000).toString());put("catalogueStale",false);put("candidates",rows)
    }
    private fun request(payload: String): List<tv.own.owntv.core.sports.WatchOption> {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/candidates") { exchange ->
            assertTrue(exchange.requestURI.rawQuery.contains("contractVersion=2"))
            assertTrue(exchange.requestURI.rawQuery.contains("eventId="))
            val bytes=payload.toByteArray();exchange.sendResponseHeaders(200,bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return try { runBlocking { BackendSportsCandidateProvider("http://127.0.0.1:${server.address.port}/candidates").options(match,source) } }
        finally { server.stop(0) }
    }
    @Test fun intendedBackendContractReachesAndroidParser() {
        val result=request(body().toString());assertEquals(1,result.size)
        assertEquals(WatchTier.GUIDE,result.single().tier);assertEquals("sport-backend:646977",result.single().channel.remoteId)
    }
    @Test fun staleMalformedLegacyOrWrongBackendFailsClosed() {
        for (payload in listOf("broken-json",body().put("catalogueStale",true).toString(),body().put("implementationId","old").toString(),body().put("contractVersion",1).toString(),body().put("eventId","other").toString())) {
            assertTrue(request(payload).isEmpty())
        }
    }
    @Test fun contradictoryAndExpiredEvidenceCannotBecomeAlternatives() {
        val bad=row();bad.getJSONArray("evidence").getJSONObject(0).put("contentMismatch",true)
        assertTrue(request(body(JSONArray().put(bad)).toString()).isEmpty())
        assertTrue(request(body(JSONArray().put(row().put("expiresAtUtc","2020-01-01T00:00:00Z"))).toString()).isEmpty())
    }
    @Test fun probeOfADifferentPlaybackOriginCannotVerifyTheAppStream() {
        val bad=row();bad.getJSONObject("playbackObservation").put("streamFingerprint","a".repeat(64))
        assertTrue(request(body(JSONArray().put(bad)).toString()).isEmpty())
    }
    @Test fun clientBoundsUnexpectedBackendFloodAndKeepsEspn2() {
        val rows=JSONArray();for(id in 1L..100L)rows.put(row(id))
        assertEquals(1,request(body(rows).toString()).size)
        val espn2=row(646973).put("displayName","AU| ESPN 2 HD")
        val espn1=row(646974).put("displayName","renamed")
        assertEquals(listOf("sport-backend:646973"),request(body(JSONArray().put(espn1).put(espn2)).toString()).map{it.channel.remoteId})
    }
}
