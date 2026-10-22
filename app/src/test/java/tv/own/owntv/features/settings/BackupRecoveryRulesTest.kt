package tv.own.owntv.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupRecoveryRulesTest {
    @Test
    fun `restore syncs only new or changed sources`() {
        val before = mapOf(1L to "same", 2L to "old", 4L to "removed")
        val after = mapOf(1L to "same", 2L to "new", 3L to "added")

        assertEquals(setOf(2L, 3L), changedSourceIds(before, after))
    }
}
