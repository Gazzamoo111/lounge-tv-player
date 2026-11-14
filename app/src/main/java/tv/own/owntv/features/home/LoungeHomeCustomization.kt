package tv.own.owntv.features.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

/** Profile-scoped presentation preferences. The user's playback, playlists and core HomeConfig are never rewritten. */
internal enum class LoungeHomeHeroSource(val title: Int) {
    SPORTS(R.string.lounge_pref_sports), TRENDING(R.string.lounge_pref_trending),
    RECENT(R.string.lounge_pref_recent), CONTINUE(R.string.lounge_pref_continue),
    WATCHLIST(R.string.lounge_pref_watchlist)
}
internal enum class LoungeHomeShelf(val title: Int) {
    KEEP(R.string.lounge_pref_keep_row), NEW(R.string.lounge_movies_new_releases),
    TRENDING(R.string.lounge_pref_trending), FEATURED(R.string.lounge_home_featured_movies),
    POPULAR(R.string.lounge_home_popular_series), LIVE(R.string.lounge_pref_live_row),
    FAV_CHANNELS(R.string.lounge_pref_favorite_channels),
    FAV_MOVIES(R.string.lounge_pref_favorite_movies), FAV_SERIES(R.string.lounge_pref_favorite_series)
}
internal data class LoungeHomePreferences(
    val source: LoungeHomeHeroSource = LoungeHomeHeroSource.SPORTS,
    val rotate: Boolean = true,
    val hiddenShelves: Set<LoungeHomeShelf> = emptySet(),
    val shelfOrder: List<LoungeHomeShelf> = LoungeHomeShelf.entries,
    val selectedSports: Set<String> = emptySet(),
    val followedFirst: Boolean = true,
) {
    fun move(row: LoungeHomeShelf, delta: Int): LoungeHomePreferences {
        val current = shelfOrder.indexOf(row)
        val target = current + delta
        if (current < 0 || target !in shelfOrder.indices) return this
        val copy = shelfOrder.toMutableList()
        copy.add(target, copy.removeAt(current))
        return this.copy(shelfOrder = copy)
    }
}

internal class LoungeHomePreferenceStore(context: Context, private val profileId: Long) {
    private val store = context.applicationContext.getSharedPreferences("lounge_home_presentation_v2", Context.MODE_PRIVATE)
    private val prefix get() = "profile_${profileId}_"
    private inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
    fun read(): LoungeHomePreferences {
        val savedOrder = store.getString(prefix + "rows", null)?.split(",")
            ?.mapNotNull { enumOrNull<LoungeHomeShelf>(it) }?.distinct().orEmpty()
        val rows = (savedOrder + LoungeHomeShelf.entries).distinct()
        return LoungeHomePreferences(
            source = enumOrNull<LoungeHomeHeroSource>(store.getString(prefix + "source", null))
                ?: LoungeHomeHeroSource.SPORTS,
            rotate = store.getBoolean(prefix + "rotate", true),
            hiddenShelves = store.getString(prefix + "hidden", "")!!.split(",")
                .mapNotNull { enumOrNull<LoungeHomeShelf>(it) }.toSet(),
            shelfOrder = rows,
            selectedSports = store.getString(prefix + "sports", "")!!.split(",")
                .filter { it.isNotBlank() }.toSet(),
            followedFirst = store.getBoolean(prefix + "followed", true),
        )
    }
    fun save(value: LoungeHomePreferences) {
        store.edit()
            .putString(prefix + "source", value.source.name)
            .putBoolean(prefix + "rotate", value.rotate)
            .putString(prefix + "rows", value.shelfOrder.joinToString(",") { it.name })
            .putString(prefix + "hidden", value.hiddenShelves.joinToString(",") { it.name })
            .putString(prefix + "sports", value.selectedSports.joinToString(","))
            .putBoolean(prefix + "followed", value.followedFirst)
            .apply()
    }
}

@Composable
internal fun LoungeHomeCustomizeScreen(
    prefs: LoungeHomePreferences,
    onChange: (LoungeHomePreferences) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    Column(
        modifier.fillMaxSize().background(Color(0xFF090D15))
            .padding(start = 180.mpx, end = 80.mpx, top = 70.mpx, bottom = 45.mpx),
        verticalArrangement = Arrangement.spacedBy(20.mpx),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.lounge_pref_customize), style = stageText(48, 850), color = Color.White)
            StageButton(stringResource(R.string.m4_done), onClick = onClose)
        }
        LazyColumn(
            Modifier.fillMaxSize().focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.mpx),
        ) {
            item { PreferenceHeading(stringResource(R.string.lounge_pref_hero_source)) }
            items(LoungeHomeHeroSource.entries, key = { "source_" + it.name }) { source ->
                PreferenceAction(
                    title = stringResource(source.title),
                    checked = prefs.source == source,
                    onClick = { onChange(prefs.copy(source = source)) },
                )
            }
            item {
                PreferenceAction(
                    title = stringResource(R.string.lounge_pref_rotation),
                    checked = prefs.rotate,
                    onClick = { onChange(prefs.copy(rotate = !prefs.rotate)) },
                )
            }
            if (prefs.source == LoungeHomeHeroSource.SPORTS) {
                item { PreferenceHeading(stringResource(R.string.lounge_pref_featured_sports)) }
                item {
                    PreferenceAction(
                        title = stringResource(R.string.lounge_pref_all_sports),
                        checked = prefs.selectedSports.isEmpty(),
                        onClick = { onChange(prefs.copy(selectedSports = emptySet())) },
                    )
                }
                items(Sport.entries, key = { "sport_" + it.name }) { sport ->
                    PreferenceAction(
                        title = sport.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        checked = sport.name in prefs.selectedSports,
                        onClick = {
                            val next = if (sport.name in prefs.selectedSports) prefs.selectedSports - sport.name
                                else prefs.selectedSports + sport.name
                            onChange(prefs.copy(selectedSports = next))
                        },
                    )
                }
                item {
                    PreferenceAction(
                        title = stringResource(R.string.lounge_pref_followed_first),
                        checked = prefs.followedFirst,
                        onClick = { onChange(prefs.copy(followedFirst = !prefs.followedFirst)) },
                    )
                }
            }
            item { PreferenceHeading(stringResource(R.string.lounge_pref_shelves)) }
            items(prefs.shelfOrder, key = { "shelf_" + it.name }) { shelf ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.mpx)) {
                    Box(Modifier.weight(1f)) {
                        PreferenceAction(
                            title = stringResource(shelf.title),
                            checked = shelf !in prefs.hiddenShelves,
                            onClick = {
                                val hidden = if (shelf in prefs.hiddenShelves) prefs.hiddenShelves - shelf
                                    else prefs.hiddenShelves + shelf
                                onChange(prefs.copy(hiddenShelves = hidden))
                            },
                        )
                    }
                    StageButton(
                        stringResource(R.string.lounge_pref_up),
                        onClick = { onChange(prefs.move(shelf, -1)) },
                    )
                    StageButton(
                        stringResource(R.string.lounge_pref_down),
                        onClick = { onChange(prefs.move(shelf, 1)) },
                    )
                }
            }
        }
    }
}
@Composable
private fun PreferenceHeading(label: String) {
    Text(label, style = stageText(25, 800), color = Color.White, modifier = Modifier.padding(top = 18.mpx, bottom = 4.mpx))
}
@Composable
private fun PreferenceAction(title: String, checked: Boolean, onClick: () -> Unit) {
    StageSurface(
        onClick = onClick, radius = 14.mpx, focusStyle = StageFocus.FX,
        modifier = Modifier.fillMaxWidth(),
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.mpx, vertical = 14.mpx),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = stageText(22, 600), color = Color.White)
            if (checked) Text("✓", style = stageText(24, 800), color = stageAccent.accent)
        }
    }
}
