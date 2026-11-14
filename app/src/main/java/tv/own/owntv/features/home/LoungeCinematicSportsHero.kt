package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.Team
import tv.own.owntv.features.sports.PremiumSportsPresentation
import tv.own.owntv.ui.lounge.LoungeLogo
import tv.own.owntv.ui.lounge.LoungePremiumTokens
import tv.own.owntv.ui.lounge.teamTint
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.stage.StageTag
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText
import androidx.compose.ui.res.stringResource

/**
 * A LIVE mark requires ESPN's explicit in-play state AND a kickoff that has occurred.
 * We never derive LIVE from a programme title or from the existence of a candidate stream.
 */
internal fun canShowHomeSportLive(match: Match, nowMs: Long): Boolean =
    match.state == MatchState.IN && match.startMs > 0L && match.startMs <= nowMs

internal fun canShowHomeSportUpcoming(match: Match, nowMs: Long): Boolean =
    match.state == MatchState.PRE && match.startMs >= nowMs

/** Only a provider-authored synopsis is shown. Don't invent descriptions from team names. */
internal fun verifiedHomeSportSynopsis(match: Match): String? =
    match.subtitle?.trim()?.takeIf { it.isNotBlank() && it != match.title.trim() }

/** Conservative sport-specific art direction, used only if there is no verified event image. */
internal fun homeSportBackdropColor(sportPath: String): Color = when (sportPath) {
    "rugby", "rugby-league" -> Color(0xFF175047)
    "soccer" -> Color(0xFF254B58)
    "cricket" -> Color(0xFF2E594F)
    "basketball" -> Color(0xFF70442F)
    "football" -> Color(0xFF36526C)
    "racing" -> Color(0xFF45476A)
    "mma" -> Color(0xFF71404F)
    "tennis" -> Color(0xFF536141)
    "hockey" -> Color(0xFF325B75)
    "baseball" -> Color(0xFF425779)
    "golf" -> Color(0xFF35634C)
    else -> LoungePremiumTokens.Elevated
}

@Composable
private fun BoxScope.HomeSportBackdrop(
    match: Match,
    verifiedEventArtworkUrl: String?,
) {
    val base = homeSportBackdropColor(match.sport.path)
    val home = teamTint(match.home?.team) ?: base
    val away = teamTint(match.away?.team) ?: LoungePremiumTokens.DeepNavy
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
        listOf(home.copy(alpha = 0.86f), base, away.copy(alpha = 0.76f)),
    ))) {
        // No fetch occurs when the event has no verified exact-match image.
        if (!verifiedEventArtworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = verifiedEventArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            listOf(LoungePremiumTokens.Ink.copy(alpha = 0.82f),
                LoungePremiumTokens.Ink.copy(alpha = 0.22f),
                LoungePremiumTokens.Ink.copy(alpha = 0.68f)),
        )))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, LoungePremiumTokens.Ink.copy(alpha = 0.76f)),
        )))
    }
}

@Composable
private fun HomeSportSide(team: Team?, modifier: Modifier = Modifier) {
    if (team == null) {
        Spacer(modifier)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.mpx)) {
        Box(
            modifier = Modifier.size(99.mpx)
                .clip(RoundedCornerShape(22.mpx))
                .background(LoungePremiumTokens.Ink.copy(alpha = 0.48f))
                .border(1.mpx, LoungePremiumTokens.Text.copy(alpha = 0.16f), RoundedCornerShape(22.mpx)),
            contentAlignment = Alignment.Center,
        ) {
            LoungeLogo(team.logoUrl,
                team.abbreviation.ifBlank { team.shortName },
                Modifier.size(82.mpx))
        }
        Text(team.shortName, style = stageText(22, 800), color = LoungePremiumTokens.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Home-only cinematic card. Reuses StageSurface focus/long-press and the original fixture
 * identity. Sports Lounge's PremiumEventCard and its navigation remain untouched.
 *
 * Only pass verifiedEventArtworkUrl after exact event-ID/date matching against an approved,
 * rights-cleared image source. Currently the caller supplies none: safe artwork fallback.
 */
@Composable
internal fun LoungeCinematicSportsHeroCard(
    match: Match,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onRemind: () -> Unit,
    verifiedEventArtworkUrl: String? = null,
) {
    val shape = RoundedCornerShape(26.mpx)
    val nowMs = System.currentTimeMillis()
    StageSurface(
        onClick = onOpen,
        radius = 26.mpx,
        focusStyle = StageFocus.FX,
        onLongClick = onRemind,
        modifier = modifier.clip(shape)
            .background(LoungePremiumTokens.Ink)
            .border(1.mpx, LoungePremiumTokens.Edge, shape),
    ) { focused ->
        HomeSportBackdrop(match, verifiedEventArtworkUrl)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 30.mpx, vertical = 22.mpx),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                Text(match.leagueName, modifier = Modifier.weight(1f), style = stageText(20, 750),
                    color = LoungePremiumTokens.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    canShowHomeSportLive(match, nowMs) ->
                        StageTag(stringResource(R.string.player_live), tint = LoungePremiumTokens.Live)
                    canShowHomeSportUpcoming(match, nowMs) ->
                        StageTag(stringResource(R.string.m6_upcoming), tint = LoungePremiumTokens.Cyan)
                }
            }
            if (match.home != null && match.away != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                    HomeSportSide(match.home?.team, Modifier.weight(1f))
                    Text(stringResource(R.string.lounge_home_vs),
                        style = stageText(21, 800), color = LoungePremiumTokens.Body)
                    HomeSportSide(match.away?.team, Modifier.weight(1f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.mpx)) {
                Text(match.title, style = stageText(27, 800), color = LoungePremiumTokens.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                verifiedHomeSportSynopsis(match)?.let {
                    Text(it, style = stageText(18, 500), color = LoungePremiumTokens.Body,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val details = listOfNotNull(
                    PremiumSportsPresentation.nzKickoffSummary(match.startMs),
                    match.venue?.trim()?.takeIf { it.isNotEmpty() },
                ).joinToString(stringResource(R.string.content_epg_bits_separator))
                Text(details, style = stageText(17, 650), color = LoungePremiumTokens.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (focused) Box(Modifier.matchParentSize().border(
            2.mpx, LoungePremiumTokens.Cyan, shape))
    }
}
