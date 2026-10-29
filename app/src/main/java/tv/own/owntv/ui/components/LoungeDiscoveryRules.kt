package tv.own.owntv.ui.components

/** Local discovery ranking, never provider availability or a claim of personalisation. */
internal object LoungeDiscoveryRules {
    data class Candidate(
        val id: Long,
        val title: String,
        val year: Int?,
        val categoryId: Long?,
        val genres: Set<String> = emptySet(),
        val rating: Double? = null,
    )
    data class Selection(val ids: List<Long> = emptyList(), val genreMatched: Boolean = false)

    fun related(target: Candidate, candidates: List<Candidate>, limit: Int = 12): Selection {
        val targetKey = LoungeVodEditorialQuality.key(target.title)
        val targetGenres = target.genres.map { it.lowercase().trim() }.filter(String::isNotBlank).toSet()
        fun overlap(c: Candidate) = c.genres.count { it.lowercase().trim() in targetGenres }
        val eligible = candidates.filter {
            it.id != target.id && LoungeVodEditorialQuality.key(it.title) != targetKey
        }
        val matched = eligible.filter { overlap(it) > 0 }
        val useGenres = matched.isNotEmpty()
        val pool = if (useGenres) matched else eligible.filter {
            target.categoryId != null && it.categoryId == target.categoryId
        }
        val ordered = pool.sortedWith(
            compareByDescending<Candidate> { if (useGenres) overlap(it) else 0 }
                .thenByDescending { it.rating ?: 0.0 }
                .thenBy { it.id },
        ).distinctBy { LoungeVodEditorialQuality.duplicateKey(it.title, it.year) }
        return Selection(ordered.take(limit.coerceIn(0, 20)).map { it.id }, useGenres)
    }
}
