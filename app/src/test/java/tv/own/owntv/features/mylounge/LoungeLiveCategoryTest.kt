package tv.own.owntv.features.mylounge

import org.junit.Assert.assertEquals
import org.junit.Test

class LoungeLiveCategoryTest {
    @Test fun sportsUseCategoryAndNames() {
        assertEquals(LoungeLiveCategory.SPORTS, loungeLiveCategory("FOX SPORTS 501", "Australia"))
        assertEquals(LoungeLiveCategory.SPORTS, loungeLiveCategory("TV One", "Rugby"))
    }
    @Test fun movieChannelsAreNotGeneralEntertainment() {
        assertEquals(LoungeLiveCategory.MOVIES, loungeLiveCategory("Sky Cinema Premiere", "Entertainment"))
        assertEquals(LoungeLiveCategory.ENTERTAINMENT, loungeLiveCategory("Sky One", "Entertainment"))
    }
    @Test fun childrenContentTakesPrecedence() {
        assertEquals(LoungeLiveCategory.KIDS_FAMILY, loungeLiveCategory("Disney Junior", "Movies"))
        assertEquals(LoungeLiveCategory.KIDS_FAMILY, loungeLiveCategory("Cartoon Network", "US TV"))
    }
}
