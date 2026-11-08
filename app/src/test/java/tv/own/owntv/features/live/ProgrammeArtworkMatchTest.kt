package tv.own.owntv.features.live

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.metadata.MetadataSearchResult
import tv.own.owntv.core.metadata.MetadataType

class ProgrammeArtworkMatchTest {
    private fun hit(id: Int, title: String, year: Int = 2024, type: MetadataType = MetadataType.MOVIE, poster: String? = "/poster.jpg", original: String? = null) =
        MetadataSearchResult(id, type, title, original, year, null, poster, 1.0)

    @Test fun `year distinguishes remakes`() {
        val q = ProgrammeArtworkMatch.query("Movie: The Thing (1982)")!!
        assertEquals("The Thing", q.title)
        assertEquals(1982, q.year)
        assertEquals(1, ProgrammeArtworkMatch.select(q, listOf(hit(1, "The Thing", 1982), hit(2, "The Thing", 2011)))?.tmdbId)
    }
    @Test fun `same name with no year is ambiguous`() {
        val q = ProgrammeArtworkMatch.query("The Thing")!!
        assertNull(ProgrammeArtworkMatch.select(q, listOf(hit(1, "The Thing", 1982), hit(2, "The Thing", 2011))))
    }
    @Test fun `movie and series collision falls back`() {
        val q = ProgrammeArtworkMatch.query("Fargo")!!
        assertNull(ProgrammeArtworkMatch.select(q, listOf(hit(1, "Fargo"), hit(2, "Fargo", type = MetadataType.TV))))
    }
    @Test fun `episode clue limits to television`() {
        val q = ProgrammeArtworkMatch.query("Fargo S02E04")!!
        assertEquals(MetadataType.TV, q.type)
        assertEquals(2, ProgrammeArtworkMatch.select(q, listOf(hit(1, "Fargo"), hit(2, "Fargo", type = MetadataType.TV)))?.tmdbId)
    }
    @Test fun `similar titles and absent images cannot match`() {
        val q = ProgrammeArtworkMatch.query("The Office")!!
        assertNull(ProgrammeArtworkMatch.select(q, listOf(hit(1, "Office Space"))))
        assertNull(ProgrammeArtworkMatch.select(q, listOf(hit(1, "The Office", poster = null))))
    }
    @Test fun `missing artwork does not resolve an identity collision`() {
        val q = ProgrammeArtworkMatch.query("Dune")!!
        assertNull(ProgrammeArtworkMatch.select(q, listOf(hit(1, "Dune", 1984, poster = null), hit(2, "Dune", 2021))))
    }
    @Test fun `original title and accents normalize without fuzzy guessing`() {
        val q = ProgrammeArtworkMatch.query("Amelie")!!
        assertEquals(1, ProgrammeArtworkMatch.select(q, listOf(hit(1, "Translated title", original = "Amélie")))?.tmdbId)
    }
    @Test fun `placeholder programmes do not consume metadata quota`() {
        for (title in listOf("News", "No programme information", "Off air", "TBA", "")) assertNull(ProgrammeArtworkMatch.query(title))
    }
    @Test fun `duplicate search entries are not ambiguous`() {
        val q = ProgrammeArtworkMatch.query("Moana")!!
        val h = hit(1, "Moana")
        assertEquals(h, ProgrammeArtworkMatch.select(q, listOf(h, h)))
    }
    @Test fun `explicit year mismatch is rejected`() {
        val q = ProgrammeArtworkMatch.query("Moana", 2016)!!
        assertNull(ProgrammeArtworkMatch.select(q, listOf(hit(1, "Moana", 2024))))
    }
}
