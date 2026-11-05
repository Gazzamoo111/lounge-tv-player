package tv.own.owntv.features.sports

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sports.model.*

/** Runs only with the explicitly started isolated QA runtime; no mock HTTP/production backend. */
class RealEventStreamIntegrationTest {
    @Test fun realMultiSportFixturesReachTheConfiguredQaBackendAndAndroidParser() = runBlocking {
        val casesFile = System.getenv("QA_EVENT_CASES_FILE")
            ?: error("Real event integration requires QA_EVENT_CASES_FILE from the read-only collector")
        val endpoint = tv.own.owntv.BuildConfig.SPORTS_CANDIDATES_URL
        assertTrue("QA must use the isolated loopback runtime", endpoint.startsWith("http://127.0.0.1:"))
        val cases=JSONArray(File(casesFile).readText())
        assertTrue("Real upcoming/live fixtures are required",cases.length()>0)
        val sports=hashSetOf<String>()
        val source=SourceEntity(id=1,name="QA-only",type=SourceType.XTREAM,url="https://example.invalid",username="qa-test",password="qa-test")
        val client=BackendSportsCandidateProvider()
        for(i in 0 until cases.length()) {
            val e=cases.getJSONObject(i).getJSONObject("event")
            if(!sports.add(e.getString("sportId")))continue
            val sport=Sport.entries.first { it.path==e.getString("sportId") }
            val feed=e.getString("competitionId")
            val match=Match(e.getString("providerEventId"),sport,feed,feed.substringAfterLast('/'),e.getString("competitionName"),
                Instant.parse(e.getString("scheduledStartUtc")).toEpochMilli(),MatchState.valueOf(e.getString("status")),e.getString("status"),e.getString("title"))
            val params=mapOf("contractVersion" to "2","eventId" to match.key,"sportId" to sport.path,"competitionId" to feed,"scheduledStartUtc" to e.getString("scheduledStartUtc"))
            val query=params.entries.joinToString("&") { (k,v)->URLEncoder.encode(k,"UTF-8")+"="+URLEncoder.encode(v,"UTF-8") }
            val connection=URL(endpoint+"?"+query).openConnection() as HttpURLConnection
            val body=try { assertEquals(200,connection.responseCode);JSONObject(connection.inputStream.bufferedReader().use{it.readText()}) } finally { connection.disconnect() }
            assertEquals(QA_SPORTS_IMPLEMENTATION,body.getString("implementationId"))
            assertEquals(16140,body.getJSONObject("coverage").getInt("catalogueScanned"))
            val parsed=client.resolve(match,source)
            assertEquals(body.getJSONArray("candidates").length(),parsed.options.size)
            assertEquals(parsed.options.size<3,parsed.coverage.coverageGap)
            assertTrue(e.getString("fixtureSource").startsWith("https://"))
        }
        assertTrue("At least three real sports must exercise this path",sports.size>=3)
    }
}
