package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LoungeCategoryOrderTest {
    @Test fun explicitMoveWinsEvenWhenItsSavedPositionExceedsSectionOrdinal() {
        val rows = listOf("UK Sports", "Rugby", "US Sports", "Cricket")
        // A saved full-rail order includes categories from earlier sections before Rugby.
        val saved = listOf("NZ", "Australia", "Movies", "Rugby")
        val fallback = compareBy<String> { LoungeEditorialOrder.categoryPriority(LoungeLiveSection.SPORTS, it) }
        assertEquals(listOf("Rugby", "US Sports", "UK Sports", "Cricket"),
            LoungeEditorialOrder.orderedCategories(rows, saved, { it }, fallback))
    }

    @Test fun untouchedCategoriesRetainEditorialOrderAndExplicitMovesStayStable() {
        val rows = listOf("Cricket", "UK Sports", "US Sports")
        val fallback = compareBy<String> { LoungeEditorialOrder.categoryPriority(LoungeLiveSection.SPORTS, it) }
        assertEquals(listOf("US Sports", "UK Sports", "Cricket"),
            LoungeEditorialOrder.orderedCategories(rows, emptyList(), { it }, fallback))
        assertEquals(listOf("UK Sports", "Cricket", "US Sports"),
            LoungeEditorialOrder.orderedCategories(rows, listOf("UK Sports", "Cricket"), { it }, fallback))
    }
}
