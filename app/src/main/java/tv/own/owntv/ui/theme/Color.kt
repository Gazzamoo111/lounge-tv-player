package tv.own.owntv.ui.theme

import androidx.compose.ui.graphics.Color
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.OwnTVPalette

/**
 * Material 3 tonal palette for OwnTV (teal-seeded). NEUTRAL + secondary/tertiary roles are
 * theme-only; the `primary` roles are seeded per [AccentColor] (default teal == these values).
 *
 * Dark uses a near-black background (#040e0b) so the panel colours pop against
 * the deep dark surface while keeping a subtle green undertone.
 *
 * **The values themselves live in core**, in [OwnTVPalette], because the mobile app renders the
 * same product and a second copy of the hex codes would drift. This file only wraps them in
 * Compose's [Color], which core cannot do — it carries the Compose runtime, not `compose-ui`.
 */

// Brand mark color (the OwnTV play logo) — constant.
val AccentCyan = Color(OwnTVPalette.AccentCyan)

// ---------------- DARK (M3 dark over near-black #040e0b) ----------------
val DarkBackground = Color(0xFF050A14) // Option A — nav + inter-panel gap surface
val DarkSurface = Color(0xFF081324)
val DarkSurfaceContainerLowest = Color(0xFF040812)
val DarkSurfaceContainerLow = Color(0xFF0A1629)
val DarkSurfaceContainer = Color(0xFF0D1C31)
val DarkSurfaceContainerHigh = Color(0xFF13243A)
val DarkSurfaceContainerHighest = Color(0xFF1A2C45)
val DarkOnSurface = Color(OwnTVPalette.DarkOnSurface)
val DarkOnSurfaceVariant = Color(0xFFB2C4DC)
val DarkOutline = Color(0xFF536987)
val DarkOutlineVariant = Color(0xFF2A3B55)
val DarkSecondary = Color(OwnTVPalette.DarkSecondary)
val DarkOnSecondary = Color(OwnTVPalette.DarkOnSecondary)
val DarkSecondaryContainer = Color(OwnTVPalette.DarkSecondaryContainer)
val DarkOnSecondaryContainer = Color(OwnTVPalette.DarkOnSecondaryContainer)
val DarkTertiary = Color(OwnTVPalette.DarkTertiary)
val DarkOnTertiary = Color(OwnTVPalette.DarkOnTertiary)
val DarkTertiaryContainer = Color(OwnTVPalette.DarkTertiaryContainer)
val DarkOnTertiaryContainer = Color(OwnTVPalette.DarkOnTertiaryContainer)
val DarkError = Color(OwnTVPalette.DarkError)

// ---------------- LIGHT (M3 light) ----------------
val LightBackground = Color(OwnTVPalette.LightBackground)
val LightSurface = Color(OwnTVPalette.LightSurface)
val LightSurfaceContainerLowest = Color(OwnTVPalette.LightSurfaceContainerLowest)
val LightSurfaceContainerLow = Color(OwnTVPalette.LightSurfaceContainerLow)
val LightSurfaceContainer = Color(OwnTVPalette.LightSurfaceContainer)
val LightSurfaceContainerHigh = Color(OwnTVPalette.LightSurfaceContainerHigh)
val LightSurfaceContainerHighest = Color(OwnTVPalette.LightSurfaceContainerHighest)
val LightOnSurface = Color(OwnTVPalette.LightOnSurface)
val LightOnSurfaceVariant = Color(OwnTVPalette.LightOnSurfaceVariant)
val LightOutline = Color(OwnTVPalette.LightOutline)
val LightOutlineVariant = Color(OwnTVPalette.LightOutlineVariant)
val LightSecondary = Color(OwnTVPalette.LightSecondary)
val LightOnSecondary = Color(OwnTVPalette.LightOnSecondary)
val LightSecondaryContainer = Color(OwnTVPalette.LightSecondaryContainer)
val LightOnSecondaryContainer = Color(OwnTVPalette.LightOnSecondaryContainer)
val LightTertiary = Color(OwnTVPalette.LightTertiary)
val LightOnTertiary = Color(OwnTVPalette.LightOnTertiary)
val LightTertiaryContainer = Color(OwnTVPalette.LightTertiaryContainer)
val LightOnTertiaryContainer = Color(OwnTVPalette.LightOnTertiaryContainer)
val LightError = Color(OwnTVPalette.LightError)
