package tv.own.owntv.ui.lounge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

internal object LoungeSurface {
    val Navy = Color(0xFF041426)
    val Panel = Color(0xED101F35)
    val Cyan = Color(0xFF3DDCFF)
    val Muted = Color(0xFFACBED4)
    val Line = Color(0xFF334760)
}

@Composable
internal fun LoungeAction(label: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    StageSurface(onClick = onClick, radius = 28.mpx, focusStyle = StageFocus.FX,
        modifier = modifier.height(58.mpx).clip(RoundedCornerShape(28.mpx))
            .background(if (selected) LoungeSurface.Cyan else LoungeSurface.Panel)
            .border(1.mpx, LoungeSurface.Line, RoundedCornerShape(28.mpx))) { focused ->
        Text(label, style = stageText(21, 700), color = if (selected) LoungeSurface.Navy else Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 22.mpx))
    }
}

@Composable
internal fun LoungeLogo(url: String?, fallback: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) AsyncImage(url, contentDescription = fallback, modifier = Modifier.fillMaxSize())
        else Text(fallback.take(3), style = stageText(24, 800), color = Color.White)
    }
}

internal fun Modifier.loungeBackdrop(): Modifier = background(Brush.verticalGradient(listOf(Color(0xFF152D46), LoungeSurface.Navy)))
