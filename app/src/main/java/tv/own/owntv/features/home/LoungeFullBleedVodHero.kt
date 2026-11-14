package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.stage.StageTitleArt
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** Every slide owns a real, playable catalogue target and actual metadata. No invented artwork. */
internal data class LoungeVodHeroFeature(
    val key: String,
    val title: String,
    val backdropUrl: String?,
    val logoUrl: String? = null,
    val metadata: String = "",
    val rating: Double? = null,
    val synopsis: String? = null,
    val onDetails: (() -> Unit)? = null,
    val onPlay: () -> Unit,
)

@Composable
internal fun LoungeFullBleedVodHero(
    items: List<LoungeVodHeroFeature>,
    autoRotate: Boolean,
    firstFocus: FocusRequester?,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    if (items.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val item = items[index.mod(items.size)]
    var logoFailed by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(items.map { it.key }, autoRotate, active, focused) {
        while (autoRotate && active && !focused && items.size > 1) {
            delay(8_000L)
            index = (index + 1).mod(items.size)
        }
    }
    StageSurface(
        onClick = item.onPlay,
        radius = 0.mpx,
        focusStyle = StageFocus.FX,
        modifier = modifier.fillMaxWidth().height(835.mpx)
            .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !focused || items.size < 2) false
                else when (event.key) {
                    Key.DirectionRight -> { index = (index + 1).mod(items.size); true }
                    Key.DirectionLeft -> { index = (index - 1).mod(items.size); true }
                    else -> false
                }
            }
            .then(if (firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Color(0xFF0D1522))) {
            if (!item.backdropUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color(0xFF00030A).copy(alpha = 0.22f),
                0.48f to Color.Transparent,
                0.73f to Color(0xFF04070C).copy(alpha = 0.50f),
                1f to Color(0xFF04070C).copy(alpha = 0.96f),
            )))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Color(0xFF03060D).copy(alpha = 0.96f),
                0.38f to Color(0xFF03060D).copy(alpha = 0.68f),
                0.80f to Color.Transparent,
            )))
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.63f)
                    .padding(start = 175.mpx, top = 120.mpx, bottom = 40.mpx),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.mpx),
            ) {
                if (!item.logoUrl.isNullOrBlank() && !logoFailed) {
                    StageTitleArt(
                        url = item.logoUrl,
                        contentDescription = item.title,
                        onFailed = { logoFailed = true },
                        modifier = Modifier.widthIn(max = 570.mpx).height(118.mpx),
                    )
                } else {
                    Text(
                        item.title,
                        style = stageText(64, 850),
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 880.mpx),
                    )
                }
                if (item.metadata.isNotBlank()) {
                    Text(item.metadata, style = stageText(20, 650), color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.rating?.takeIf { it > 0.0 }?.let { score ->
                    StageTag(stringResource(R.string.lounge_pref_rating_number, score))
                }
                item.synopsis?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(summary, style = stageText(22, 430), color = Color.White.copy(alpha = 0.86f),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 730.mpx))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.mpx),
                    verticalAlignment = Alignment.CenterVertically) {
                    StageButton(text = stringResource(R.string.home_trending_play), onClick = item.onPlay)
                    item.onDetails?.let { open ->
                        StageButton(text = stringResource(R.string.lounge_on_demand_more_info), onClick = open)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.mpx),
                    verticalAlignment = Alignment.CenterVertically) {
                    for (dot in items.indices.take(10)) {
                        Box(
                            Modifier.size(if (dot == index.mod(items.size)) 13.mpx else 8.mpx)
                                .background(
                                    if (dot == index.mod(items.size)) Color.White
                                    else Color.White.copy(alpha = 0.35f), CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}
