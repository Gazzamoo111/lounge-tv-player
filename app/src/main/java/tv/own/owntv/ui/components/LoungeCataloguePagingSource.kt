package tv.own.owntv.ui.components

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.useReaderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.settings.SettingsRepository.SortMode

/** IDs first, hydrate only one page. All orderings end in a unique ID for stable boundaries. */
internal class LoungeCataloguePagingSource<T : Any>(
    private val database: OwnTVDatabase,
    private val table: String,
    private val sourceIds: List<Long>,
    private val categoryId: Long?,
    private val query: String,
    private val sort: SortMode,
    private val filter: LoungeBrowseFilter,
    private val hydrate: suspend (List<Long>) -> List<T>,
    private val idOf: (T) -> Long,
) : PagingSource<Int, T>() {
    init {
        require(table == "movies" || table == "series")
        val observer = object : InvalidationTracker.Observer(table) {
            override fun onInvalidated(tables: Set<String>) { invalidate() }
        }
        database.invalidationTracker.addObserver(observer)
        registerInvalidatedCallback { database.invalidationTracker.removeObserver(observer) }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { page ->
            page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = withContext(Dispatchers.IO) {
        try {
            val offset = params.key ?: 0
            val arguments = mutableListOf<Any>()
            val where = mutableListOf<String>()
            where += "sourceId IN (${sourceIds.ifEmpty { listOf(-1L) }.joinToString(",") { "?" }})"
            arguments.addAll(sourceIds.ifEmpty { listOf(-1L) })
            categoryId?.let { where += "categoryId = ?"; arguments.add(it) }
            filter.ids?.let { ids ->
                if (ids.isEmpty()) where += "0" else {
                    where += "id IN (${ids.joinToString(",") { "?" }})"; arguments.addAll(ids)
                }
            }
            filter.releaseYears?.let { years ->
                where += "COALESCE(year, parsedYear, 0) BETWEEN ? AND ?"
                arguments.add(years.first.toLong()); arguments.add(years.last.toLong())
            }
            if (query.isNotBlank()) { where += "instr(lower(name), lower(?)) > 0"; arguments.add(query) }
            val order = if (filter.releaseYearDescending) "COALESCE(year, parsedYear, 0) DESC, name COLLATE NOCASE ASC, id ASC" else when (sort) {
                SortMode.ALPHA -> "name COLLATE NOCASE ASC, id ASC"
                SortMode.RATING -> "rating DESC, name COLLATE NOCASE ASC, id ASC"
                SortMode.DATE_ADDED -> "addedAt DESC, sortOrder DESC, id DESC"
                else -> "sourceId ASC, sortOrder ASC, name COLLATE NOCASE ASC, id ASC"
            }
            val sql = "SELECT id FROM $table WHERE ${where.joinToString(" AND ")} ORDER BY $order LIMIT ? OFFSET ?"
            arguments.add(params.loadSize.toLong()); arguments.add(offset.toLong())
            val ids = database.useReaderConnection { connection ->
                connection.usePrepared(sql) { statement ->
                    arguments.forEachIndexed { index, value ->
                        when (value) {
                            is Long -> statement.bindLong(index + 1, value)
                            is String -> statement.bindText(index + 1, value)
                        }
                    }
                    buildList { while (statement.step()) add(statement.getLong(0)) }
                }
            }
            val byId = if (ids.isEmpty()) emptyMap() else hydrate(ids).associateBy(idOf)
            LoadResult.Page(
                data = ids.mapNotNull(byId::get),
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                nextKey = if (ids.size < params.loadSize) null else offset + ids.size,
            )
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (problem: Exception) { LoadResult.Error(problem) }
    }
}
