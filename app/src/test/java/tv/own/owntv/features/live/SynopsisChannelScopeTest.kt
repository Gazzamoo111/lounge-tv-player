package tv.own.owntv.features.live

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class SynopsisChannelScopeTest {
    private fun channel(id: Long, category: Long, source: Long = 1L) = ChannelEntity(
        id = id, sourceId = source, categoryId = category, name = "Channel $id",
        streamUrl = "https://example.test/$id.ts", remoteId = "$id",
    )

    @Test
    fun `switching away from Sky Sport browses the newly playing channel category`() {
        val sky = listOf(channel(10, 100), channel(11, 100))
        val entertainment = listOf(channel(20, 200), channel(21, 200), channel(22, 200))
        assertEquals(entertainment, synopsisChannelScope(entertainment[1], entertainment, sky))
    }

    @Test
    fun `stale playback rail cannot replace a missing current-channel anchor`() {
        val sky = listOf(channel(10, 100), channel(11, 100))
        val playing = channel(99, 200)
        assertEquals(listOf(playing), synopsisChannelScope(playing, listOf(channel(20, 200)), sky))
    }

    @Test
    fun `synopsis excludes unrelated categories and sources`() {
        val playing = channel(20, 200)
        val next = channel(21, 200)
        assertEquals(
            listOf(playing, next),
            synopsisChannelScope(
                playing,
                listOf(channel(10, 100), playing, channel(23, 200, 2), next),
                listOf(channel(10, 100)),
            ),
        )
    }

    @Test
    fun `synthetic sports retains only its current match candidates`() {
        val playing = channel(-1, 100)
        val options = listOf(playing, channel(-2, 300))
        assertEquals(options, synopsisChannelScope(playing, emptyList(), options))
    }

    @Test
    fun `synthetic sport with missing anchor does not use stale candidates`() {
        val playing = channel(-4, 300)
        assertEquals(
            listOf(playing),
            synopsisChannelScope(playing, emptyList(), listOf(channel(-1, 100))),
        )
    }
}
