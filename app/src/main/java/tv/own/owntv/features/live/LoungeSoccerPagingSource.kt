package tv.own.owntv.features.live
import androidx.paging.PagingSource
import androidx.paging.PagingState
import tv.own.owntv.core.database.entity.ChannelEntity
import kotlinx.coroutines.CancellationException

internal class LoungeSoccerPagingSource(
    private val loader: suspend () -> List<ChannelEntity>,
) : PagingSource<Int, ChannelEntity>() {
    override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? = state.anchorPosition

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> {
        return try {
            val ordered = loader()
            val offset = (params.key ?: 0).coerceIn(0, ordered.size)
            val end = (offset + params.loadSize).coerceAtMost(ordered.size)
            LoadResult.Page(
                data = ordered.subList(offset, end),
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                nextKey = if (end == ordered.size) null else end,
                itemsBefore = offset,
                itemsAfter = ordered.size - end,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
