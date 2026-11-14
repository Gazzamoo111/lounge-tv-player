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
internal fun homeSportBackdropColor(sport: tv.own.owntv.core.sports.model.Sport): Color = when (sport) {
    tv.own.owntv.core.sports.model.Sport.RUGBY,
    tv.own.owntv.core.sports.model.Sport.RUGBY_LEAGUE -> Color(0xFF175047)
    tv.own.owntv.core.sports.model.Sport.SOCCER -> Color(0xFF254B58)
    tv.own.owntv.core.sports.model.Sport.CRICKET -> Color(0xFF2E594F)
    tv.own.owntv.core.sports.model.Sport.BASKETBALL -> Color(0xFF70442F)
    tv.own.owntv.core.sports.model.Sport.FOOTBALL -> Color(0xFF36526C)
    tv.own.owntv.core.sports.model.Sport.RACING -> Color(0xFF45476A)
    tv.own.owntv.core.sports.model.Sport.MMA -> Color(0xFF71404F)
    tv.own.owntv.core.sports.model.Sport.TENNIS -> Color(0xFF536141)
    tv.own.owntv.core.sports.model.Sport.HOCKEY -> Color(0xFF325B75)
    tv.own.owntv.core.sports.model.Sport.BASEBALL -> Color(0xFF425779)
    tv.own.owntv.core.sports.model.Sport.GOLF -> Color(0xFF35634C)
    else -> LoungePremiumTokens.Elevated
}

/**
 * A broadcast-led fallback, not an unrelated sports photograph. Exact-event art
 * can replace this when an approved image is tied to the fixture identity.
 */
@Composable
private fun BoxScope.HomeSportBackdrop(
    match: Match,
    verifiedEventArtworkUrl: String?,
) {
    val base = homeSportBackdropColor(match.sport)
    val home = teamTint(match.home?.team) ?: base
    val away = teamTint(match.away?.team) ?: LoungePremiumTokens.DeepNavy
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
        listOf(LoungePremiumTokens.Ink, base.copy(alpha = 0.94f), LoungePremiumTokens.DeepNavy),
    ))) {
        if (!verifiedEventArtworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = verifiedEventArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // Soft broadcast light falls on the two crests; the left remains legible.
            Box(Modifier.matchParentSize().background(Brush.radialGradient(
                listOf(home.copy(alpha = 0.55f), away.copy(alpha = 0.20f), Color.Transparent),
                radius = 950f,
            )))
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                listOf(LoungePremiumTokens.Ink.copy(alpha = 0.90f),
                    LoungePremiumTokens.Ink.copy(alpha = 0.72f),
                    LoungePremiumTokens.Ink.copy(alpha = 0.16f)),
            )))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(LoungePremiumTokens.Ink.copy(alpha = 0.08f), Color.Transparent,
                LoungePremiumTokens.Ink.copy(alpha = 0.63f)),
        )))
    }
}

@Composable
private fun HomeSportBadge(label: String, tint: Color) {
    val shape = RoundedCornerShape(22.mpx)
    Box(
        Modifier.clip(shape)
            .background(tint.copy(alpha = 0.22f))
            .border(1.mpx, tint.copy(alpha = 0.84f), shape)
            .padding(horizontal = 16.mpx, vertical = 8.mpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = stageText(16, 850), color = LoungePremiumTokens.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeSportSide(team: Team?, modifier: Modifier = Modifier) {
    if (team == null) {
        Spacer(modifier)
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.mpx)) {
        Box(
            modifier = Modifier.size(144.mpx)
                .clip(RoundedCornerShape(32.mpx))
                .background(LoungePremiumTokens.Ink.copy(alpha = 0.38f))
                .border(1.mpx, LoungePremiumTokens.Text.copy(alpha = 0.15f), RoundedCornerShape(32.mpx)),
            contentAlignment = Alignment.Center,
        ) {
            LoungeLogo(team.logoUrl,
                team.abbreviation.ifBlank { team.shortName },
                Modifier.size(124.mpx))
        }
        Text(team.shortName, style = stageText(22, 800), color = LoungePremiumTokens.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Home-only broadcast card. The match identity, remote focus behaviour, open
 * action and reminder behaviour remain owned by the original Home carousel.
 * No image is inferred from team names: only approved exact-event art may be supplied.
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
    val live = canShowHomeSportLive(match, nowMs)
    val upcoming = canShowHomeSportUpcoming(match, nowMs)
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
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 36.mpx, vertical = 28.mpx),
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(if (match.home != null && match.away != null) 0.55f else 1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    match.leagueName.trim().takeIf { it.isNotEmpty() }?.let {
                        HomeSportBadge(it, LoungePremiumTokens.Cyan)
                    }
                    if (live) {
                        HomeSportBadge(stringResource(R.string.player_live), LoungePremiumTokens.Live)
                    } else if (upcoming) {
                        HomeSportBadge(stringResource(R.string.m6_upcoming), LoungePremiumTokens.Cyan)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(14.mpx)) {
                    Text(
                        match.title, style = stageText(42, 850),
                        color = LoungePremiumTokens.Text,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    verifiedHomeSportSynopsis(match)?.let {
                        Text(it, style = stageText(19, 500), color = LoungePremiumTokens.Body,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                val details = listOfNotNull(
                    PremiumSportsPresentation.nzKickoffSummary(match.startMs),
                    match.venue?.trim()?.takeIf { it.isNotEmpty() },
                ).joinToString(stringResource(R.string.content_epg_bits_separator))
                if (details.isNotBlank()) {
                    Text(details, style = stageText(20, 700), color = LoungePremiumTokens.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (match.home != null && match.away != null) {
                Row(
                    modifier = Modifier.weight(0.45f).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.mpx),
                ) {
                    HomeSportSide(match.home?.team, Modifier.weight(1f))
                    Text(stringResource(R.string.lounge_home_vs),
                        style = stageText(30, 850), color = LoungePremiumTokens.Text)
                    HomeSportSide(match.away?.team, Modifier.weight(1f))
                }
            }
        }
        if (focused) Box(Modifier.matchParentSize().border(
            2.mpx, LoungePremiumTokens.Cyan, shape))
    }
}
