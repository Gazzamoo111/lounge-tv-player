package tv.own.owntv.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.OnDemandDesign
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/**
 * On Demand has a horizontal navigation language rather than the Live TV capsule.
 * The existing shell still owns routes, content focus, playback and Back handling.
 */
@Composable
internal fun OnDemandTopNavigation(
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    selectedItemFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    enterContent: (() -> Boolean)?,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf(
        MainSection.HOME,
        MainSection.MOVIES,
        MainSection.SERIES,
        MainSection.SEARCH,
        MainSection.MY_LOUNGE,
        MainSection.DOWNLOADS,
        MainSection.MORE,
    ).filter { it == MainSection.MORE || it == MainSection.SEARCH || it in visibleSections }
    val active = if (selected == MainSection.SETTINGS) MainSection.MORE
        else selected.takeIf { it in sections } ?: MainSection.HOME

    Box(modifier, contentAlignment = Alignment.TopStart) {
        Row(
            Modifier.padding(start = 64.mpx, top = 26.mpx)
                .background(OnDemandDesign.midnight.copy(alpha = 0.91f), RoundedCornerShape(25.mpx))
                .padding(horizontal = 12.mpx, vertical = 8.mpx)
                .onFocusChanged { if (it.hasFocus) onFocused() }
                // The old floating rail dispatched Down asynchronously from onExit,
                // cancelling the transition before any content acquired focus. That
                // trapped Fire TV D-pad navigation in the top menu.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        enterContent?.invoke() == true ||
                            runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)
                    } else false
                }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEach { section ->
                val isActive = section == active
                StageSurface(
                    onClick = { onSelect(section) },
                    radius = 18.mpx,
                    focusStyle = StageFocus.PRIMARY,
                    idle = if (isActive) {
                        Modifier.background(OnDemandDesign.electricLime.copy(alpha = 0.16f), RoundedCornerShape(18.mpx))
                    } else Modifier,
                    modifier = Modifier
                        .height(52.mpx)
                        .widthIn(min = 80.mpx)
                        .then(if (isActive) Modifier.focusRequester(selectedItemFocusRequester) else Modifier),
                ) { focused ->
                    Text(
                        text = stringResource(section.labelRes),
                        color = when {
                            focused -> OnDemandDesign.midnight
                            isActive -> OnDemandDesign.electricLime
                            else -> OnDemandDesign.secondaryText
                        },
                        style = stageText(18, if (focused || isActive) 800 else 600),
                        modifier = Modifier.padding(horizontal = 16.mpx),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
