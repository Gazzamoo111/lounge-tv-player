package tv.own.owntv.features.live

import androidx.paging.PagingSource
import androidx.paging.PagingState
import tv.own.owntv.core.database.entity.ChannelEntity
import java.util.Locale

/** Only the independently imported NZ Freeview category gets fixed launch order. */
internal object LoungeNzOrder {
    fun priority(name: String): Int {
        val n = name.trim().lowercase(Locale.ROOT)
        return when {
            n == "tvnz 1" || n == "tv 1" || n == "one" -> 0
            n == "tvnz 2" || n == "tv 2" -> 1
            n == "three" || n == "tv3" || n == "tv 3" -> 2
            n.contains("whakaata") || n == "māori tv" || n == "maori tv" -> 3
            n == "te reo" || n == "te reo tv" -> 4
            else -> 10
        }
    }
}

/** Small NZ playlist: reorder rows without modifying playlist order in Room. */
internal class LoungeNzPriorityPagingSource(
    private val loader: suspend () -> List<ChannelEntity>,
) : PagingSource<Int, ChannelEntity>() {
    override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? = state.anchorPosition

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> {
        return try {
            val ordered = loader().withIndex()
                .sortedWith(compareBy<IndexedValue<ChannelEntity>>(
                    { LoungeNzOrder.priority(it.value.name) }, { it.index },
                ))
                .map { it.value }
            val offset = (params.key ?: 0).coerceIn(0, ordered.size)
            val end = (offset + params.loadSize).coerceAtMost(ordered.size)
            LoadResult.Page(
                data = ordered.subList(offset, end),
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                nextKey = if (end == ordered.size) null else end,
                itemsBefore = offset,
                itemsAfter = ordered.size - end,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
