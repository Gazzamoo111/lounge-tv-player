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
        assertTrue(result?.get("fixture-a").orEmpty().contains("thumb/abc.jpg"))
    }
    @Test fun noArtIsAValidResult() {
        assertEquals(emptyMap<String, String>(),
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
           "url":"https://www.thesportsdb.com/images/media/event/banner/skinny.jpg"}
        ]}"""
        assertEquals(emptyMap<String, String>(), HeroArtworkParser.parse(json))
    }
    @Test fun invalidResponsesCannotReplaceCachedValidArt() {
        assertNull(HeroArtworkParser.parse("garbage"))
        assertNull(HeroArtworkParser.parse("""{"status":"error","arts":[]}"""))
    }
}
