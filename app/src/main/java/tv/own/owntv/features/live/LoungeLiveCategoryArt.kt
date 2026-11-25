package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import tv.own.owntv.ui.theme.LocalStagePalette
import tv.own.owntv.ui.theme.mpx

/**
 * A restrained visual signature for Lounge's existing top-level Live accordion headings.
 * The colour never alters category identity, category order, focus keys or folder selection.
 * Subcategory tiles deliberately keep the existing uncluttered StageSheetItem styling.
 */
internal fun loungeLiveSectionColour(section: LoungeLiveSection): Color = when (section) {
    LoungeLiveSection.NEW_ZEALAND -> Color(0xFF287C76)
    LoungeLiveSection.AUSTRALIA -> Color(0xFF386C90)
    LoungeLiveSection.SPORTS -> Color(0xFF3278B4)
    LoungeLiveSection.ENTERTAINMENT -> Color(0xFF74549D)
    LoungeLiveSection.MOVIES -> Color(0xFF975C65)
    LoungeLiveSection.DOCUMENTARIES -> Color(0xFF4D866E)
    LoungeLiveSection.KIDS -> Color(0xFFA38A43)
    LoungeLiveSection.SEASONAL -> Color(0xFF5D7890)
    LoungeLiveSection.STREAMING -> Color(0xFF5A75AD)
    LoungeLiveSection.US_UK -> Color(0xFF56718C)
    LoungeLiveSection.MUSIC -> Color(0xFF79699C)
    LoungeLiveSection.PPV -> Color(0xFF795A72)
    LoungeLiveSection.INTERNATIONAL -> Color(0xFF627485)
    LoungeLiveSection.MY_CATEGORIES, LoungeLiveSection.FAVOURITE_FOLDERS -> Color(0xFF477C8A)
}

@Composable
internal fun Modifier.loungePremiumLiveSectionHeading(section: LoungeLiveSection): Modifier {
    val colour = loungeLiveSectionColour(section)
    val isLight = LocalStagePalette.current.isLight
    val shape = RoundedCornerShape(18.mpx)
    return this
        .background(
            Brush.horizontalGradient(
                listOf(
                    colour.copy(alpha = if (isLight) 0.16f else 0.44f),
                    colour.copy(alpha = if (isLight) 0.06f else 0.13f),
                ),
            ),
            shape,
        )
        .border(1.mpx, colour.copy(alpha = if (isLight) 0.38f else 0.42f), shape)
}
