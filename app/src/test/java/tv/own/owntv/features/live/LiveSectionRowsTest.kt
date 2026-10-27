package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSectionRowsTest {
    private val sections = listOf(
        null, null, LoungeLiveSection.SPORTS, LoungeLiveSection.SPORTS,
        LoungeLiveSection.SPORTS, LoungeLiveSection.MOVIES, LoungeLiveSection.MOVIES,
        LoungeLiveSection.STREAMING,
    )
    private val all = List(sections.size) { true }

    @Test fun collapsedShowsEveryHeadingButNoHiddenCategories() {
        val rows = liveSectionRows(sections, all, null, false)
        assertEquals(
            listOf(
                LiveSectionRow.ChannelFolder(0), LiveSectionRow.ChannelFolder(1),
                LiveSectionRow.Heading(LoungeLiveSection.SPORTS),
                LiveSectionRow.Heading(LoungeLiveSection.MOVIES),
                LiveSectionRow.Heading(LoungeLiveSection.STREAMING),
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

    @Test fun interleavedSportsFoldersStayDirectlyUnderSportsHeading() {
        val mixed = listOf(
            null, LoungeLiveSection.SPORTS, LoungeLiveSection.MOVIES,
            LoungeLiveSection.SPORTS, LoungeLiveSection.KIDS,
            LoungeLiveSection.SPORTS, LoungeLiveSection.MOVIES,
        )
        val rows = liveSectionRows(mixed, List(mixed.size) { true }, LoungeLiveSection.SPORTS, false)
        assertEquals(
            listOf(
                LiveSectionRow.ChannelFolder(0),
                LiveSectionRow.Heading(LoungeLiveSection.SPORTS),
                LiveSectionRow.ChannelFolder(1),
                LiveSectionRow.ChannelFolder(3),
                LiveSectionRow.ChannelFolder(5),
                LiveSectionRow.Heading(LoungeLiveSection.MOVIES),
                LiveSectionRow.Heading(LoungeLiveSection.KIDS),
            ),
            rows,
        )
    }

    @Test fun sportsBadgeCountEqualsExpandedFolderCountWithMixedManualOrder() {
        val mixed = buildList<LoungeLiveSection?> {
            add(null)
            repeat(25) { index ->
                add(LoungeLiveSection.SPORTS)
                if (index % 3 == 0) add(LoungeLiveSection.MOVIES)
                if (index % 5 == 0) add(LoungeLiveSection.KIDS)
            }
        }
        val rows = liveSectionRows(mixed, List(mixed.size) { true }, LoungeLiveSection.SPORTS, false)
        val headingIndex = rows.indexOf(LiveSectionRow.Heading(LoungeLiveSection.SPORTS))
        assertEquals(25, mixed.count { it == LoungeLiveSection.SPORTS })
        assertTrue(headingIndex >= 0)
        assertEquals(
            mixed.indices.filter { mixed[it] == LoungeLiveSection.SPORTS },
            rows.drop(headingIndex + 1).take(25)
                .filterIsInstance<LiveSectionRow.ChannelFolder>().map { it.index },
        )
    }

    @Test fun searchFindsCategoriesEvenWhenTheirHeadingIsCollapsed() {
        val matches = listOf(false, false, false, true, false, false, true, false)
        val rows = liveSectionRows(sections, matches, null, true)
        assertEquals(listOf(LiveSectionRow.ChannelFolder(3), LiveSectionRow.ChannelFolder(6)), rows)
    }

    @Test fun hiddenCategoriesAreAbsentFromBothBadgeAndExpandedRows() {
        val visibleAfterProfileFilters = listOf(
            null, LoungeLiveSection.SPORTS, LoungeLiveSection.MOVIES, LoungeLiveSection.SPORTS,
        )
        val rows = liveSectionRows(
            visibleAfterProfileFilters,
            List(visibleAfterProfileFilters.size) { true },
            LoungeLiveSection.SPORTS,
            false,
        )
        assertEquals(2, liveSectionFolderCount(visibleAfterProfileFilters, LoungeLiveSection.SPORTS))
        assertEquals(
            2,
            rows.filterIsInstance<LiveSectionRow.ChannelFolder>()
                .count { visibleAfterProfileFilters[it.index] == LoungeLiveSection.SPORTS },
        )
    }

    @Test fun replacingSourcesOrRestartingHasNoStaleSectionCount() {
        val firstSync = listOf(LoungeLiveSection.SPORTS, LoungeLiveSection.SPORTS)
        val resync = listOf(LoungeLiveSection.SPORTS, LoungeLiveSection.MOVIES, LoungeLiveSection.MOVIES)
        assertEquals(2, liveSectionFolderCount(firstSync, LoungeLiveSection.SPORTS))
        assertEquals(1, liveSectionFolderCount(resync, LoungeLiveSection.SPORTS))
        assertEquals(2, liveSectionFolderCount(resync.toList(), LoungeLiveSection.MOVIES))
    }
}
