package tv.own.owntv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Midnight Electric is deliberately scoped to the standalone On Demand build.
 * Source metadata, playback settings, and the Live TV brand are not modified.
 */
object OnDemandDesign {
    val midnight = Color(0xFF081528)
    val electricLime = Color(0xFFB7FF35)
    val slateBlue = Color(0xFF182840)
    val coolWhite = Color(0xFFF5F8FC)
    val secondaryText = Color(0xFFB3C2D6)
    val subdued = Color(0xFF879CB8)

    fun colors(base: OwnTVColors): OwnTVColors = base.copy(
        isDark = true,
        background = midnight,
        surface = midnight,
        surfaceContainerLowest = Color(0xFF06101F),
        surfaceContainerLow = Color(0xFF101F34),
        surfaceContainer = slateBlue,
        surfaceContainerHigh = Color(0xFF203550),
        surfaceContainerHighest = Color(0xFF2B405C),
        onSurface = coolWhite,
        onSurfaceVariant = secondaryText,
        outline = Color(0xFF40536D),
        outlineVariant = Color(0xFF2C3E56),
        primary = electricLime,
        onPrimary = midnight,
        primaryContainer = Color(0xFF344622),
        onPrimaryContainer = coolWhite,
        secondary = secondaryText,
        onSecondary = midnight,
        secondaryContainer = slateBlue,
        onSecondaryContainer = coolWhite,
        tertiary = electricLime,
        onTertiary = midnight,
        tertiaryContainer = slateBlue,
        onTertiaryContainer = coolWhite,
        focusBorder = electricLime,
        focusGlow = electricLime.copy(alpha = 0.30f),
        accentOnVideo = electricLime,
        onAccentOnVideo = midnight,
    )

    val stage: StagePalette = StagePalette.Dark.copy(
        text = coolWhite,
        muted = secondaryText,
        dim = subdued,
        itemText = coolWhite,
        menuItemText = coolWhite,
        tagText = secondaryText,
        page = midnight,
        glassTint = slateBlue,
        glassOff = Color(0xFF111F33),
        body = secondaryText,
        navText = coolWhite,
        fxText = midnight,
        fxLine = midnight,
        wash = midnight,
        segmentOn = electricLime.copy(alpha = 0.14f),
        keycap = coolWhite.copy(alpha = 0.09f),
    )
}
