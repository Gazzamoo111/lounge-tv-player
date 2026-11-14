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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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
import tv.own.owntv.ui.stage.StageButton
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
/** Vary fallback art direction by fixture: split teams, broadcast lighting, or competition.
 * Approved exact-event artwork takes priority when an exact matching asset exists.
 */
@Composable
private fun BoxScope.HomeSportBackdrop(match: Match, verifiedEventArtworkUrl: String?) {
    val base = homeSportBackdropColor(match.sport)
    val home = teamTint(match.home?.team) ?: base
    val away = teamTint(match.away?.team) ?: LoungePremiumTokens.DeepNavy
    val variant = (match.key.hashCode() and Int.MAX_VALUE) % 3
    Box(Modifier.matchParentSize().background(when (variant) {
        0 -> Brush.horizontalGradient(listOf(home.copy(alpha = 0.78f), base, away.copy(alpha = 0.80f)))
        1 -> Brush.linearGradient(listOf(home.copy(alpha = 0.80f), LoungePremiumTokens.Ink, away.copy(alpha = 0.85f)))
        else -> Brush.horizontalGradient(listOf(base, LoungePremiumTokens.DeepNavy, away.copy(alpha = 0.75f)))
    })) {
        if (!verifiedEventArtworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = verifiedEventArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            if (variant == 1) Box(Modifier.matchParentSize().background(Brush.radialGradient(
                listOf(home.copy(alpha = 0.45f), away.copy(alpha = 0.20f), Color.Transparent),
                radius = 1300f,
            )))
            if (variant == 2) Box(Modifier.matchParentSize().background(Brush.radialGradient(
                listOf(away.copy(alpha = 0.38f), Color.Transparent), radius = 1800f,
            )))
            // Broadcast texture is deliberately subtle so authentic crests remain dominant.
            Box(Modifier.matchParentSize().drawBehind {
                val gap = if (variant == 0) 94f else 125f
                val alpha = if (variant == 0) 0.023f else 0.030f
                for (i in -15..40) {
                    val x = i * gap
                    drawLine(Color.White.copy(alpha = alpha),
                        start = Offset(x, 0f), end = Offset(x - size.height * 0.40f, size.height),
                        strokeWidth = 16f)
                }
            })
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent,
                LoungePremiumTokens.Ink.copy(alpha = 0.72f),
                LoungePremiumTokens.Ink.copy(alpha = 0.98f)),
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
        verticalArrangement = Arrangement.spacedBy(15.mpx)) {
        Box(Modifier.size(210.mpx)
            .clip(RoundedCornerShape(110.mpx))
            .background(LoungePremiumTokens.Ink.copy(alpha = 0.32f))
            .border(2.mpx, Color.White.copy(alpha = 0.48f), RoundedCornerShape(110.mpx)),
            contentAlignment = Alignment.Center) {
            LoungeLogo(team.logoUrl,
                team.abbreviation.ifBlank { team.shortName },
                Modifier.size(164.mpx))
        }
        Text(team.shortName, style = stageText(24, 800), color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Full-bleed broadcast hero: common layout, variable team/competition/event art treatments. */
@Composable
internal fun LoungeCinematicSportsHeroCard(
    match: Match,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onRemind: () -> Unit,
    verifiedEventArtworkUrl: String? = null,
) {
    val nowMs = System.currentTimeMillis()
    val live = canShowHomeSportLive(match, nowMs)
    val upcoming = canShowHomeSportUpcoming(match, nowMs)
    StageSurface(
        onClick = onOpen,
        radius = 0.mpx,
        focusStyle = StageFocus.FX,
        onLongClick = onRemind,
        modifier = modifier.background(LoungePremiumTokens.Ink),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            HomeSportBackdrop(match, verifiedEventArtworkUrl)
            if (match.home != null && match.away != null) {
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .padding(start = 185.mpx, end = 70.mpx, top = 135.mpx),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeSportSide(match.home?.team, Modifier.weight(1f))
                    Spacer(Modifier.weight(0.5f))
                    HomeSportSide(match.away?.team, Modifier.weight(1f))
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 175.mpx, end = 65.mpx, bottom = 73.mpx),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.mpx),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    verticalAlignment = Alignment.CenterVertically) {
                    match.leagueName.trim().takeIf { it.isNotEmpty() }?.let {
                        HomeSportBadge(it, LoungePremiumTokens.Cyan)
                    }
                    if (live) HomeSportBadge(
                        stringResource(R.string.lounge_pref_live_mark), LoungePremiumTokens.Live
                    )
                    else if (upcoming) HomeSportBadge(
                        stringResource(R.string.lounge_pref_upcoming_mark), LoungePremiumTokens.Cyan
                    )
                }
                Text(
                    match.title.uppercase(),
                    style = stageText(62, 900),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    PremiumSportsPresentation.nzKickoffSummary(match.startMs),
                    match.leagueName.trim().takeIf { it.isNotEmpty() },
                ).joinToString(stringResource(R.string.content_epg_bits_separator))
                if (details.isNotBlank()) {
                    Text(details, style = stageText(21, 700), color = Color.White,
                        textAlign = TextAlign.Center, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                StageButton(
                    text = stringResource(R.string.lounge_pref_match_details),
                    onClick = onOpen,
                )
            }
        }
    }
}
