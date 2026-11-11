package tv.own.owntv.features.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideRowFocusArgsTest {

    @Test
    fun `focused row gets the cursor and cell mode`() {
        val args = guideRowFocusArgs(
            index = 3,
            focusedRow = 3,
            cursorTime = 1_700_000_000_000L,
            inCellMode = true,
            gridHasFocus = true,
        )
        assertEquals(1_700_000_000_000L, args.cursorTime)
        assertTrue(args.cellMode)
        assertTrue(args.selectedRow)
    }

    @Test
    fun `unfocused row gets no cursor and no cell mode`() {
        val args = guideRowFocusArgs(
            index = 2,
            focusedRow = 3,
            cursorTime = 1_700_000_000_000L,
            inCellMode = true,
            gridHasFocus = true,
        )
        assertEquals(NO_CURSOR, args.cursorTime)
        assertFalse(args.cellMode)
        assertFalse(args.selectedRow)
    }

    @Test
    fun `selected row also requires guide grid focus`() {
        assertFalse(guideRowFocusArgs(1, 1, 5L, true, false).selectedRow)
        assertFalse(guideRowFocusArgs(0, 1, 5L, true, true).selectedRow)
        assertTrue(guideRowFocusArgs(1, 1, 5L, false, true).selectedRow)
    }

    @Test
    fun `focused row retains cursor outside cell mode`() {
        val args = guideRowFocusArgs(4, 4, 9L, false, true)
        assertFalse(args.cellMode)
        assertEquals(9L, args.cursorTime)
        assertTrue(args.selectedRow)
    }
}
