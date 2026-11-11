package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import tv.own.owntv.ui.components.OwnTVIcon

class LoungeCategoryIconsTest {
    @Test
    fun everyMajorSectionHasADistinctMark() {
        val assigned = LoungeLiveSection.entries.associateWith(LoungeEditorialOrder::icon)
        assertEquals(assigned.size, assigned.values.toSet().size)
        assertNotEquals(OwnTVIcon.PERSON, assigned.getValue(LoungeLiveSection.KIDS))
    }

    @Test
    fun importantCustomerFoldersDoNotShareGenericArtwork() {
        val categories = listOf(
            LoungeLiveSection.SPORTS to "Sports · US Sports",
            LoungeLiveSection.SPORTS to "Sports · UK Sports",
            LoungeLiveSection.SPORTS to "Sports · Rugby Union",
            LoungeLiveSection.SPORTS to "Sports · Rugby League",
            LoungeLiveSection.SPORTS to "Sports · Cricket",
            LoungeLiveSection.SPORTS to "Sports · Football",
            LoungeLiveSection.SPORTS to "Sports · F1",
            LoungeLiveSection.SPORTS to "Sports · UFC",
            LoungeLiveSection.SPORTS to "Sports · WWE",
            LoungeLiveSection.SPORTS to "Sports · NBA",
            LoungeLiveSection.SPORTS to "Sports · NFL",
            LoungeLiveSection.SPORTS to "Sports · NHL",
            LoungeLiveSection.SPORTS to "Sports · MLB",
            LoungeLiveSection.SPORTS to "Sports · Tennis",
            LoungeLiveSection.SPORTS to "Sports · Golf",
            LoungeLiveSection.PPV to "PPV · Live Events",
            LoungeLiveSection.MOVIES to "Movies · Premieres",
            LoungeLiveSection.ENTERTAINMENT to "Entertainment · Series",
            LoungeLiveSection.ENTERTAINMENT to "Entertainment · General",
            LoungeLiveSection.KIDS to "Kids & Family · TV",
            LoungeLiveSection.DOCUMENTARIES to "Documentaries & Lifestyle · Documentaries",
            LoungeLiveSection.DOCUMENTARIES to "Documentaries & Lifestyle · Lifestyle",
            LoungeLiveSection.SEASONAL to "24/7 · Classics",
            LoungeLiveSection.INTERNATIONAL to "Relax · Nature",
        )
        val assigned = categories.map { (section, label) ->
            label to LoungeEditorialOrder.categoryIcon(section, label)
        }
        assertEquals(assigned.map { it.first }, assigned.map { it.first }.distinct())
        assertEquals(
            "Every listed customer category should be visually distinct",
            assigned.size,
            assigned.map { it.second }.toSet().size,
        )
    }

    @Test
    fun classifierIsStableForCanonicalAndProviderStyleLabels() {
        assertEquals(OwnTVIcon.AMERICAN_FOOTBALL, LoungeEditorialOrder.categoryIcon(LoungeLiveSection.SPORTS, "US| NFL NETWORK"))
        assertEquals(OwnTVIcon.RUGBY_LEAGUE, LoungeEditorialOrder.categoryIcon(LoungeLiveSection.SPORTS, "AU| NRL TV"))
        assertEquals(OwnTVIcon.RELAX_LEAF, LoungeEditorialOrder.categoryIcon(null, "4K RELAX UHD 3840P"))
        assertEquals(OwnTVIcon.KIDS_KITE, LoungeEditorialOrder.categoryIcon(LoungeLiveSection.KIDS, "Kids & Family · TV"))
    }
}
