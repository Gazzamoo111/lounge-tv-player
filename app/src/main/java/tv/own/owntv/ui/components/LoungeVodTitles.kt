package tv.own.owntv.ui.components

import tv.own.owntv.core.metadata.TitleNormalizer

/**
 * Customer-facing title only. Never rewrite the BEST catalogue's source name:
 * playback, source IDs, sync, search and provider matching keep the original.
 */
private val LOUNGE_PROVIDER_PREFIX = Regex(
    "^(?i)(?:(?:D\\+|NF|AMZN|APTV|HBO|DSNY|ATV|NETFLIX|DISNEY\\+|PRIME|APPLE\\s?TV|HULU|PEACOCK|PARAMOUNT\\+|STAN)\\s+)+",
)

fun loungeVodTitle(sourceName: String): String {
    val normalized = TitleNormalizer.normalize(sourceName).query.trim()
    val candidate = normalized.ifBlank { sourceName.trim() }
    return LOUNGE_PROVIDER_PREFIX.replace(candidate, "").trim().ifBlank { sourceName.trim() }
}
