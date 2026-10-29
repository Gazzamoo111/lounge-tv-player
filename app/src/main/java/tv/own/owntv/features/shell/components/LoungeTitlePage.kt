package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVPopup
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.stage.StageTitleArt
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

internal data class LoungeRelatedCard(val id: Long, val title: String, val posterUrl: String?)

/** Explicit-open title page. No player, WebView, stream request or trailer is created here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoungeTitlePage(
    identity: Long,
    details: MediaDetailsUi,
    series: Boolean,
    favorite: Boolean,
    recommendations: List<LoungeRelatedCard>,
    genreMatched: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onRelated: (Long) -> Unit,
    onExit: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    val scroll = rememberLazyListState()
    var logoFailed by remember(details.logoUrl) { mutableStateOf(false) }
    OwnTVPopup(onDismissRequest = onExit, stageLayout = true, stageScaled = false) {
        BackHandler(onBack = onExit)
        LaunchedEffect(identity) {
            scroll.scrollToItem(0)
            withFrameNanos { }
            playFocus.requestFocus()
        }
        Box(Modifier.fillMaxSize().background(StageColors.Wash).trapAllFocusExit()) {
            details.backdropUrl?.let { art ->
                AsyncImage(
                    model = art, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(820.mpx),
                )
            }
            Box(Modifier.fillMaxWidth().height(820.mpx).background(Brush.horizontalGradient(
                listOf(StageColors.Wash.copy(alpha = .96f), StageColors.Wash.copy(alpha = .60f), Color.Transparent),
            )))
            Box(Modifier.fillMaxWidth().height(860.mpx).background(Brush.verticalGradient(
                0f to Color.Transparent, .60f to Color.Transparent, 1f to StageColors.Wash,
            )))
            LazyColumn(
                state = scroll,
                contentPadding = PaddingValues(top = 76.mpx, bottom = 80.mpx),
                verticalArrangement = Arrangement.spacedBy(32.mpx),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "title") {
                    Column(Modifier.padding(horizontal = 84.mpx).widthIn(max = 1120.mpx)) {
                        if (!details.logoUrl.isNullOrBlank() && !logoFailed) {
                            StageTitleArt(details.logoUrl, null, Modifier.width(760.mpx).height(150.mpx), onFailed = { logoFailed = true })
                        } else {
                            Text(details.title, style = stageText(66, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (details.metaLine.isNotBlank()) Text(details.metaLine, style = stageText(23, 600), color = StageColors.Text, modifier = Modifier.padding(top = 18.mpx))
                        if (details.genres.isNotEmpty()) Text(details.genres.take(4).joinToString(stringResource(R.string.content_metadata_separator)), style = stageText(21, 500), color = StageColors.Body, modifier = Modifier.padding(top = 10.mpx))
                        details.plot?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = stageText(24, 400), color = StageColors.Body, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.mpx).widthIn(max = 1000.mpx))
                        }
                        Row(Modifier.padding(top = 26.mpx).focusGroup(), horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                            StageButton(
                                text = stringResource(if (series) R.string.content_episodes else R.string.lounge_discovery_play),
                                onClick = onPlay, modifier = Modifier.focusRequester(playFocus), icon = OwnTVIcon.PLAY, tinted = true,
                            )
                            StageButton(
                                text = stringResource(if (favorite) R.string.lounge_discovery_in_list else R.string.lounge_discovery_add_list),
                                onClick = onFavorite,
                            )
                        }
                    }
                }
                if (details.cast.isNotEmpty()) item(key = "cast") {
                    Column {
                        LoungeDetailHeading(stringResource(R.string.content_media_cast))
                        // A bounded, wrapping cast row stays reachable by vertical D-pad traversal.
                        // Cast is informational, so it creates no dead-end focus targets.
                        FlowRow(Modifier.padding(horizontal = 84.mpx), horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalArrangement = Arrangement.spacedBy(18.mpx)) {
                            details.cast.take(10).forEach { member ->
                                Column(Modifier.width(138.mpx), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(112.mpx).clip(CircleShape).background(StageColors.Body.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                                        val photo = MetadataImages.profile(member.profilePath)
                                        if (photo != null) AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        else Text(member.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), style = stageText(28, 700), color = StageColors.Text)
                                    }
                                    Text(member.name, style = stageText(17, 600), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx))
                                }
                            }
                        }
                    }
                }
                if (recommendations.isNotEmpty()) item(key = "related") {
                    Column {
                        LoungeDetailHeading(stringResource(if (genreMatched) R.string.lounge_discovery_more_like_this else R.string.lounge_discovery_same_category))
                        key(identity) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 84.mpx, vertical = 20.mpx),
                                horizontalArrangement = Arrangement.spacedBy(28.mpx), modifier = Modifier.focusGroup(),
                            ) {
                                items(recommendations, key = { it.id }) { card ->
                                    StagePoster(card.title, { onRelated(card.id) }, 220.mpx, 330.mpx) {
                                        VodPosterArt(card.posterUrl, if (series) OwnTVIcon.SERIES else OwnTVIcon.MOVIES)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoungeDetailHeading(text: String) {
    Text(text, style = stageText(28, 800), color = StageColors.Text, modifier = Modifier.padding(start = 84.mpx, bottom = 18.mpx))
}
