package tv.own.owntv.features.vod

import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity

/**
 * Fetch only candidate titles through the existing FTS index rather than paging over tens of
 * thousands of BEST rows and discarding almost everything after loading. Empty pages cannot
 * drive Paging 3's scroll prefetch, so post-page whitelist filtering alone is not sufficient.
 *
 * Works with future provider updates because all queries target the current local catalogue,
 * not a captured snapshot of a customer's first import.
 */
internal object LoungeVodCatalogueReader {
    private const val MATCHES_PER_BATCH = 400

    suspend fun movies(
        dao: MovieDao,
        sourceIds: List<Long>,
        hiddenCategories: Set<Long> = emptySet(),
    ): List<MovieEntity> {
        if (sourceIds.isEmpty()) return emptyList()
        return LoungeVodPolicy.movieSearchQueries()
            .flatMap { phrase -> dao.searchListFts(phrase, sourceIds, MATCHES_PER_BATCH) }
            .asSequence()
            .filter(LoungeVodPolicy::allowsMovie)
            .filter { it.categoryId == null || it.categoryId !in hiddenCategories }
            .groupBy(LoungeVodPolicy::movieKey)
            .values
            .mapNotNull { versions -> versions.maxByOrNull { it.qualityRank } }
            .sortedBy(LoungeVodPolicy::movieRank)
    }

    suspend fun series(
        dao: SeriesDao,
        sourceIds: List<Long>,
        hiddenCategories: Set<Long> = emptySet(),
    ): List<SeriesEntity> {
        if (sourceIds.isEmpty()) return emptyList()
        return LoungeVodPolicy.seriesSearchQueries()
            .flatMap { phrase -> dao.searchListFts(phrase, sourceIds, MATCHES_PER_BATCH) }
            .asSequence()
            .filter(LoungeVodPolicy::allowsSeries)
            .filter { it.categoryId == null || it.categoryId !in hiddenCategories }
            .groupBy(LoungeVodPolicy::seriesKey)
            .values
            .mapNotNull { versions -> versions.maxByOrNull { it.qualityRank } }
            .sortedBy(LoungeVodPolicy::seriesRank)
    }
}
