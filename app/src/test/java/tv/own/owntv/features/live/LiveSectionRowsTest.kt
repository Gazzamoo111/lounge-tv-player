package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSectionRowsTest {
    private val sections = listOf(
        null, null, LoungeLiveSection.SPORTS, LoungeLiveSection.SPORTS,
        LoungeLiveSection.SPORTS, LoungeLiveSection.MOVIES, LoungeLiveSection.MOVIES,
        LoungeLiveSection.NEWS,
    )
    private val all = List(sections.size) { true }

    @Test fun collapsedShowsEveryHeadingButNoHiddenCategories() {
        val rows = liveSectionRows(sections, all, null, false)
        assertEquals(
            listOf(
                LiveSectionRow.ChannelFolder(0), LiveSectionRow.ChannelFolder(1),
                LiveSectionRow.Heading(LoungeLiveSection.SPORTS),
                LiveSectionRow.Heading(LoungeLiveSection.MOVIES),
                LiveSectionRow.Heading(LoungeLiveSection.NEWS),
            ), rows,
        )
    }

    @Test fun openingOneSectionKeepsOriginalFolderIndices() {
        val rows = liveSectionRows(sections, all, LoungeLiveSection.SPORTS, false)
        assertEquals(listOf(0, 1, 2, 3, 4),
            rows.filterIsInstance<LiveSectionRow.ChannelFolder>().map { it.index })
        assertEquals(3, rows.count { it is LiveSectionRow.Heading })
        assertFalse(rows.contains(LiveSectionRow.ChannelFolder(5)))
    }

    @Test fun searchFindsCategoriesEvenWhenTheirHeadingIsCollapsed() {
        val matches = listOf(false, false, false, true, false, false, true, false)
        val rows = liveSectionRows(sections, matches, null, true)
        assertEquals(listOf(LiveSectionRow.ChannelFolder(3), LiveSectionRow.ChannelFolder(6)), rows)
    }
}
