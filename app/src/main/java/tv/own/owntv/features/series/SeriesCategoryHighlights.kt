package tv.own.owntv.features.series

import androidx.room.useReaderConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.live.LiveKey
import java.util.Calendar
import kotlin.random.Random

/**
 * Series category-scoped, read-only editorial rows. The full PagingSource is never altered.
 * Read at most a few hundred candidate IDs per rail using Room's own read connection,
 * then hydrate them in one DAO call. No playlist refresh or network request is needed.
 */
internal data class SeriesCategoryHighlights(
    val categoryKey: LiveKey? = null,
    val day: Long = 0L,
    val featured: List<SeriesEntity> = emptyList(),
    val newReleases: List<SeriesEntity> = emptyList(),
)

internal class SeriesCategoryHighlightsReader(
    private val database: OwnTVDatabase,
    private val seriesDao: SeriesDao,
) {
    suspend fun load(
        key: LiveKey,
        profileId: Long,
        sourceIds: List<Long>,
        hiddenCategories: Set<Long>,
        customization: SectionCustomizations,
        folderContextKey: String?,
        day: Long,
    ): SeriesCategoryHighlights = withContext(Dispatchers.IO) {
        if (profileId < 0 || sourceIds.isEmpty()) {
            return@withContext SeriesCategoryHighlights(categoryKey = key, day = day)
        }

        val year = Calendar.getInstance().get(Calendar.YEAR)
        val common = makeScope(key, profileId, sourceIds, hiddenCategories)

        // The candidate pool is deliberately bigger than the 20 cards displayed. Rotation
        // changes the actual titles shown, not just their order, without reading full catalogues.
        val featuredIds = selectIds(
            common,
            extraWhere = "",
            extraParams = emptyList(),
            order = "COALESCE(m.rating, 0) DESC, m.qualityRank DESC, COALESCE(m.year, m.parsedYear, 0) DESC, m.id",
            limit = 280,
        )
        val releaseIds = selectIds(
            common,
            extraWhere = " AND COALESCE(m.year, m.parsedYear, 0) BETWEEN ? AND ?",
            extraParams = listOf((year - 2).toLong(), year.toLong()),
            order = "COALESCE(m.year, m.parsedYear, 0) DESC, COALESCE(m.rating, 0) DESC, m.qualityRank DESC, m.id",
            limit = 160,
        )
        val candidates = (featuredIds + releaseIds).distinct()
        if (candidates.isEmpty()) return@withContext SeriesCategoryHighlights(categoryKey = key, day = day)

        val byId = seriesDao.getSeriesByIds(candidates).associateBy { it.id }
        fun visible(m: SeriesEntity): Boolean {
            val itemKey = CustomizeKeys.series(m)
            return m.categoryId !in hiddenCategories &&
                itemKey !in customization.hiddenItems &&
                !(key is LiveKey.Folder && folderContextKey != null &&
                    customization.movedFromOrigin[itemKey] == folderContextKey)
        }

        fun uniqueShows(ids: List<Long>): List<SeriesEntity> {
            val names = HashSet<String>()
            return ids.mapNotNull(byId::get).filter(::visible).filter { show ->
                val title = show.canonicalTitle.ifBlank { show.name }.lowercase()
                    .replace(Regex("^(?:d\\+|nf|amzn|hbo|netflix|disney\\+|prime)\\s+"), "")
                    .trim()
                names.add(title)
            }
        }

        val newReleases = uniqueShows(releaseIds).take(MAX_ROW_ITEMS)
        val recentIds = newReleases.mapTo(HashSet()) { it.id }
        val seeded = uniqueShows(featuredIds).take(FEATURED_POOL)
            .shuffled(Random((day xor key.hashCode().toLong()).toInt()))
        val featured = (seeded.filterNot { it.id in recentIds } + seeded)
            .distinctBy { it.id }.take(MAX_ROW_ITEMS)

        SeriesCategoryHighlights(
            categoryKey = key,
            day = day,
            featured = featured,
            newReleases = newReleases,
        )
    }

    private data class QueryScope(val joins: String, val where: String, val parameters: List<Any>)

    private fun makeScope(
        key: LiveKey,
        profileId: Long,
        sourceIds: List<Long>,
        hiddenCategories: Set<Long>,
    ): QueryScope {
        val params = mutableListOf<Any>()
        val joins = when (key) {
            is LiveKey.Custom -> {
                params.add(profileId)
                params.add(key.id)
                " INNER JOIN custom_category_members cm ON cm.itemId = m.id AND cm.profileId = ? AND cm.mediaType = 'SERIES' AND cm.contextKey = ?"
            }
            LiveKey.Favorites -> {
                params.add(profileId)
                " INNER JOIN favorites fav ON fav.itemId = m.id AND fav.mediaType = 'SERIES' AND fav.profileId = ?"
            }
            LiveKey.History -> {
                params.add(profileId)
                " INNER JOIN watch_history wh ON wh.itemId = m.id AND wh.mediaType = 'SERIES' AND wh.profileId = ?"
            }
            else -> ""
        }

        val where = StringBuilder(" WHERE m.sourceId IN (")
            .append(sourceIds.joinToString(",") { "?" })
            .append(") AND m.posterUrl IS NOT NULL AND TRIM(m.posterUrl) <> ''")
        params.addAll(sourceIds)

        if (key is LiveKey.Folder) {
            where.append(" AND m.categoryId = ?")
            params.add(key.id)
        }
        if (hiddenCategories.isNotEmpty()) {
            where.append(" AND (m.categoryId IS NULL OR m.categoryId NOT IN (")
                .append(hiddenCategories.joinToString(",") { "?" }).append("))")
            params.addAll(hiddenCategories)
        }
        return QueryScope(joins, where.toString(), params)
    }

    private suspend fun selectIds(
        scope: QueryScope,
        extraWhere: String,
        extraParams: List<Long>,
        order: String,
        limit: Int,
    ): List<Long> {
        val sql = "SELECT DISTINCT m.id FROM series m" + scope.joins + scope.where +
            extraWhere + " ORDER BY " + order + " LIMIT " + limit
        return database.useReaderConnection { conn ->
            conn.usePrepared(sql) { statement ->
                (scope.parameters + extraParams).forEachIndexed { index, value ->
                    when (value) {
                        is Long -> statement.bindLong(index + 1, value)
                        is String -> statement.bindText(index + 1, value)
                        else -> error("Unsupported category query parameter")
                    }
                }
                buildList {
                    while (statement.step()) add(statement.getLong(0))
                }
            }
        }
    }

    companion object {
        const val MAX_ROW_ITEMS = 20
        private const val FEATURED_POOL = 120
    }
}
