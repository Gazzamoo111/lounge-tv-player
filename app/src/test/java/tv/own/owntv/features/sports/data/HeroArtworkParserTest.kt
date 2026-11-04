package tv.own.owntv.features.sports.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroArtworkParserTest {
    @Test fun exactEventThumbOnTrustedSourceIsAccepted() {
        val result = HeroArtworkParser.parse("""
            {"status":"ok","arts":[{"key":"fixture-a","source":"thesportsdb",
            "confidence":"exact","url":"https://www.thesportsdb.com/images/media/event/thumb/abc.jpg"}]}
        """.trimIndent())
        assertNotNull(result)
        assertEquals(1, result?.size)
        assertTrue(result?.get("fixture-a")?.heroArtUrl.orEmpty().contains("thumb/abc.jpg"))
    }

    @Test fun originalTeamAndCompetitionBadgesAreCachedAtFullProviderResolution() {
        val result = HeroArtworkParser.parse("""
          {"status":"ok","arts":[{"key":"rl","source":"thesportsdb","confidence":"exact",
          "teamLogos":[
            {"name":"New Zealand Kiwis","url":"https://r2.thesportsdb.com/images/media/team/badge/kiwis.png/small"},
            {"name":"Samoa","url":"https://r2.thesportsdb.com/images/media/team/badge/samoa.png"}
          ],
          "competitionLogoUrl":"https://r2.thesportsdb.com/images/media/league/badge/pacific.png/medium"}]}
        """.trimIndent())
        val assets = result?.get("rl")
        assertEquals(
            "https://r2.thesportsdb.com/images/media/team/badge/kiwis.png",
            assets?.teamLogoUrls?.get(heroLogoKey("New Zealand Kiwis")),
        )
        assertEquals(
            "https://r2.thesportsdb.com/images/media/league/badge/pacific.png",
            assets?.competitionLogoUrl,
        )
    }

    @Test fun noArtIsAValidResult() {
        assertEquals(emptyMap<String, HeroEventAssets>(),
            HeroArtworkParser.parse("""{"status":"ok","arts":[]}"""))
    }

    @Test fun unverifiedAndRandomPhotographyAreRejected() {
        val json = """{"status":"ok","arts":[
          {"key":"a","source":"thesportsdb","confidence":"likely",
           "url":"https://www.thesportsdb.com/images/media/event/thumb/abc.jpg"},
          {"key":"b","source":"thesportsdb","confidence":"exact",
           "url":"https://images.pexels.com/photos/1234/photo.jpg"},
          {"key":"c","source":"thesportsdb","confidence":"exact",
           "url":"https://www.thesportsdb.com/images/media/team/banner/photo.jpg"},
          {"key":"d","source":"thesportsdb","confidence":"exact",
           "url":"https://www.thesportsdb.com/images/media/event/banner/skinny.jpg"},
          {"key":"e","source":"thesportsdb","confidence":"exact",
           "teamLogos":[{"name":"Kiwis","url":"https://example.org/kiwis.png"}]}
        ]}"""
        assertEquals(emptyMap<String, HeroEventAssets>(), HeroArtworkParser.parse(json))
    }

    @Test fun invalidResponsesCannotReplaceCachedValidArt() {
        assertNull(HeroArtworkParser.parse("garbage"))
        assertNull(HeroArtworkParser.parse("""{"status":"error","arts":[]}"""))
    }
}
