package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.brand.PixelMark as CorePixelMark
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.theme.stageAccent

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    val settings: SettingsRepository = koinInject()
    val applied = remember(context) { AppIconSwitcher.applied(context) }
    // The old remember(context) value never updated while the settings screen stayed
    // open. Observe the selected colour so the rail and branding react immediately.
    // The Android TV launcher continues to use AppIconSwitcher independently.
    val selected by settings.appIcon.collectAsStateWithLifecycle(initialValue = applied)
    return selected
}

/** Settings › Accent-colored logo: the play triangle of every in-app logo in the accent (default on). */
@Composable
private fun accentTriangle(): Color? {
    val settings: SettingsRepository = koinInject()
    val on by settings.brandAccentTriangle.collectAsStateWithLifecycle(initialValue = true)
    return if (on) stageAccent.accent else null
}

/**
 * The icon's flat mark (no shadow, no next card); at 32 dp and below the simpler small drawing. Pixel
 * above 32 dp is its dot matrix ([PixelMark]). With Accent-colored logo on, the play triangle takes the
 * accent: on a card a white triangle is drawn over the card's own and tinted (Pixel has none).
 * [followAccent] false draws the icon as the launcher shows it (the icon pickers).
 */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    Image(
        painter = painterResource(when (icon) {
            AppIcon.EGGSHELL -> R.drawable.loungetv_icon_variant_eggshell
            AppIcon.PETROL -> R.drawable.loungetv_icon_variant_petrol
            AppIcon.SUNFLOWER -> R.drawable.loungetv_icon_variant_sunflower
            AppIcon.COBALT -> R.drawable.loungetv_icon_variant_cobalt
            AppIcon.TOMATO -> R.drawable.loungetv_icon_variant_tomato
            AppIcon.BOARD -> R.drawable.loungetv_icon_variant_board
            AppIcon.OLIVE -> R.drawable.loungetv_icon_variant_olive
            AppIcon.OLIVE_CREAM -> R.drawable.loungetv_icon_variant_olive_cream
            AppIcon.PIXEL -> R.drawable.loungetv_icon_variant_pixel
        }),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/**
 * "Pixel": the dot-matrix TV set (core's [CorePixelMark], the same grid as `owntv_mark_pixel`), the dots
 * of the play triangle in [triangle].
 */
@Composable
fun PixelMark(modifier: Modifier = Modifier, triangle: Color = stageAccent.accent) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        CorePixelMark.dots.forEach { d ->
            val color = when (d.kind) {
                CorePixelMark.Kind.BODY -> PixelBody
                CorePixelMark.Kind.TRIANGLE -> triangle
                CorePixelMark.Kind.SIDE -> PixelSide
            }
            drawCircle(color, CorePixelMark.DOT_RADIUS * u, Offset(d.x * u, d.y * u))
        }
        drawCircle(PixelPower, CorePixelMark.POWER_RADIUS * u, Offset(CorePixelMark.POWER_X * u, CorePixelMark.POWER_Y * u))
    }
}

private val PixelBody = Color(CorePixelMark.BODY)
private val PixelSide = Color(CorePixelMark.SIDE)
private val PixelPower = Color(CorePixelMark.POWER)

/**
 * The wordmark: lowercase "own" in cream, "tv" in [tv] (the accent), drawn from
 * core's two vectors at [width]; its height follows the 988 × 182 drawing.
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier, tv: Color = stageAccent.accent, own: Color = WordmarkCream) {
    Image(
        painter = painterResource(R.drawable.loungetv_original_wordmark_only),
        contentDescription = null,
        modifier = modifier.width(width).height(width * 173f / 1595f),
    )
}

val WordmarkCream = Color(0xFFFFF6EE)

/**
 * The lockup (Brand tab, C column): the mark, then the wordmark at 2.75 × the mark's width (62 →
 * 170 in the design). Side by side the gap is 0.23 × the mark (14 / 62); [stacked] (the preview pane)
 * puts the mark above with a 0.2 × gap.
 */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, markSize: Int = 36, stacked: Boolean = false) {
    val icon = rememberAppliedIcon()
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.18f).dp),
        ) {
            BrandMark(icon, markSize.dp)
            Wordmark((markSize * 4.1f).dp)
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.20f).dp),
        ) {
            BrandMark(icon, markSize.dp)
            Wordmark((markSize * 4.1f).dp)
        }
    }
}
