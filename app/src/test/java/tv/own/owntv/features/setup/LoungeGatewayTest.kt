package tv.own.owntv.features.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.model.SourceType

class LoungeGatewayTest {
    @Test fun `fresh Lounge login uses managed gateway identity`() {
        assertEquals("connect.loungetv.nz", loungeHost(LOUNGE_XTREAM_GATEWAY))
        assertEquals(LOUNGE_XTREAM_USER_AGENT, loungeXtreamUserAgent(LOUNGE_XTREAM_GATEWAY, ""))
        assertEquals(LOUNGE_XTREAM_USER_AGENT, loungeXtreamUserAgent("https://connect.loungetv.nz:443/path", "Custom"))
    }

    @Test fun `known legacy Lounge Xtream hosts migrate and BYO sources do not`() {
        assertTrue(isLegacyLoungeXtream(SourceType.XTREAM, "http://line.bestnz.vip:80"))
        assertTrue(isLegacyLoungeXtream(SourceType.XTREAM, "https://line.loungeconnect247.xyz/player_api.php"))
        assertFalse(isLegacyLoungeXtream(SourceType.XTREAM, "https://example.test"))
        assertFalse(isLegacyLoungeXtream(SourceType.M3U, "http://line.bestnz.vip/list.m3u"))
        assertEquals("Custom", loungeXtreamUserAgent("https://example.test", "Custom"))
    }

    @Test fun `migration drops only a legacy provider guide`() {
        assertNull(loungeGuideAfterGatewayMigration("http://line.bestnz.vip/xmltv.php"))
        assertEquals(
            "https://guide.example.test/guide.xml",
            loungeGuideAfterGatewayMigration("https://guide.example.test/guide.xml"),
        )
        assertNull(loungeGuideAfterGatewayMigration(null))
    }
}
