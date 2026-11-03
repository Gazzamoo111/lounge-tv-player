package tv.own.owntv.features.setup

import tv.own.owntv.core.model.SourceType

internal const val LOUNGE_XTREAM_GATEWAY = "https://connect.loungetv.nz"
internal const val LOUNGE_XTREAM_USER_AGENT = "LoungeTV/Android/5.2.3.4"

private val LEGACY_LOUNGE_HOSTS = setOf(
    "line.bestnz.vip",
    "line.loungeconnect247.xyz",
)

internal fun loungeHost(raw: String): String =
    raw.trim().lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')

internal fun isLegacyLoungeXtream(type: SourceType, url: String): Boolean =
    type == SourceType.XTREAM && loungeHost(url) in LEGACY_LOUNGE_HOSTS

internal fun loungeXtreamUserAgent(server: String, current: String): String =
    if (loungeHost(server) == loungeHost(LOUNGE_XTREAM_GATEWAY)) LOUNGE_XTREAM_USER_AGENT else current

internal fun loungeGuideAfterGatewayMigration(epgUrl: String?): String? =
    epgUrl?.takeUnless { loungeHost(it) in LEGACY_LOUNGE_HOSTS }
