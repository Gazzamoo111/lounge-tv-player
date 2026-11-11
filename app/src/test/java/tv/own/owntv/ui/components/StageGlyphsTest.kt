package tv.own.owntv.ui.components

import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.ui.stage.StageGlyph

class StageGlyphsTest {
    private val loungeCategoryMarks = listOf(
        "NZ_FERN", "AU_ROO", "RELAX_LEAF",
        "ENTERTAINMENT_SPOTLIGHT", "KIDS_KITE", "DOCUMENTARY_COMPASS", "LIFESTYLE_HOME",
        "CONTINUOUS_PLAY", "EVENT_TICKET", "US_SPORTS", "UK_SPORTS",
        "RUGBY_UNION", "RUGBY_LEAGUE", "CRICKET", "FOOTBALL", "MOTORSPORT", "MMA", "WRESTLING",
        "BASKETBALL", "AMERICAN_FOOTBALL", "HOCKEY", "BASEBALL", "TENNIS", "GOLF",
    )

    // Every core glyph but the phone-only two finds its OwnTVIcon by name; Lounge-only marks are explicit.
    @Test
    fun everyCoreAndLoungeGlyphHasAnOwnTVIcon() = assertEquals(
        ((StageGlyph.entries.map { it.name } - setOf("PLUS", "MOON")) + loungeCategoryMarks).toSet(),
        StageGlyphPaths.keys.map { it.name }.toSet(),
    )

    @Test
    fun everyLoungeCategoryVectorParsesAndContainsNoPlatformEmoji() {
        loungeCategoryMarks.forEach { name ->
            val icon = OwnTVIcon.valueOf(name)
            val path = requireNotNull(StageGlyphPaths[icon])
            assertTrue("$name must use vector path data", path.startsWith("M"))
            assertTrue("$name must not contain emoji/text glyphs", path.all { it.code in 0x20..0x7e })
            PathParser().parsePathString(path).toPath()
        }
        val paths = loungeCategoryMarks.map { requireNotNull(StageGlyphPaths[OwnTVIcon.valueOf(it)]) }
        assertEquals("Lounge category marks must not reuse the same vector", paths.size, paths.map { it.replace(Regex("\\s+"), "") }.toSet().size)
    }

    @Test
    fun categoryVectorsStayWithinTheSharedCoordinateGrid() {
        // All Lounge-only marks use absolute paths. Validate every coordinate/control point,
        // including the small sports details, so new art cannot silently clip outside the canvas.
        loungeCategoryMarks.forEach { name ->
            val path = requireNotNull(StageGlyphPaths[OwnTVIcon.valueOf(name)])
            assertTrue("$name must use absolute vector commands", path.none { it in "mlhvcsqtaz" })
            Regex("-?[0-9]+(?:\\.[0-9]+)?").findAll(path).forEach { number ->
                assertTrue("$name exceeds the 24-unit coordinate grid: ${number.value}",
                    number.value.toFloat() in 0f..24f)
            }
        }
    }
}
