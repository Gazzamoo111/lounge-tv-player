package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideDisplayKindTest {
    @Test fun loungeXmltvUsesBrandedLabel() {
        assertEquals(GuideDisplayKind.LOUNGE, guideDisplayKind(LOUNGE_EPG_URL))
    }

    @Test fun xtreamXmltvWithCredentialsUsesPrimaryGuideLabel() {
        val url = "http://example.invalid/xmltv.php?username=test&password=test"
        assertEquals(GuideDisplayKind.PROVIDER, guideDisplayKind(url))
    }

    @Test fun unrecognisedUserAddedXmltvKeepsOwnName() {
        assertEquals(GuideDisplayKind.CUSTOM, guideDisplayKind("https://example.invalid/guide.xml"))
    }
}
