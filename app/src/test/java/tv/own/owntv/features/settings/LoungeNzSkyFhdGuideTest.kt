package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoungeNzSkyFhdGuideTest {
    @Test fun skySportOneToNineGetCorrectNzGuideId() {
        for (number in 1..9) {
            assertEquals("skysport" + number + ".nz",
                LoungeNzSkyFhdGuide.epgId(
                    "NZ| SKY SPORT " + number + " FHD",
                    "NZ| SKY SPORT ᴿᴬᵂ", true))
            assertEquals("skysport" + number + ".nz",
                LoungeNzSkyFhdGuide.epgId(
                    "SKY SPORT" + number + " FHD",
                    "NZ| SKY SPORTS NZ", true))
        }
    }

    @Test fun exclusionsPreventIncorrectSportOrForeignMatches() {
        for (channel in listOf("SKY SPORT 3 RAW", "SKY SPORT 3 HD",
            "SKY SPORT POP UP 1 FHD", "SKY SPORT SELECT FHD",
            "SKY SPORT PREMIER LEAGUE FHD", "SKY SPORT 10 FHD",
            "FOX SPORTS 3 FHD")) {
            assertNull(channel, LoungeNzSkyFhdGuide.epgId(
                channel, "NZ| SKY SPORT ᴿᴬᵂ", true))
        }
        assertNull(LoungeNzSkyFhdGuide.epgId("SKY SPORT 2 FHD", "UK| SPORTS", true))
        assertNull(LoungeNzSkyFhdGuide.epgId("SKY SPORT 2 FHD", "NZ| SKY SPORT ᴿᴬᵂ", false))
    }
}
