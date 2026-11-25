package tv.own.owntv.features.customize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageText

/** A TV-first picker: OK immediately saves, Back never tunes another stream. */
@Composable
fun FavouriteFolderPicker(
    folders: List<MoveTarget>,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onDismiss() }
    tv.own.owntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.lounge_v2_save_favourite_folder),
        body = stringResource(R.string.lounge_v2_favourite_folder_description),
        width = 760.mpx,
        buttons = {
            tv.own.owntv.ui.stage.StagePopupButton(
                stringResource(R.string.common_cancel), onClick = onDismiss, primary = false,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 460.mpx),
            verticalArrangement = Arrangement.spacedBy(4.mpx),
        ) {
            item(key = "create") {
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.lounge_v2_new_favourite_folder),
                    onClick = onCreate,
                    modifier = Modifier.focusRequester(firstFocus),
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.ADD) },
                )
            }
            items(folders, key = { it.id }) { folder ->
                tv.own.owntv.ui.stage.StagePopupOption(
                    title = FavouriteFolders.displayName(folder.displayName),
                    onClick = { onSelect(folder.id) },
                    leading = { tv.own.owntv.ui.stage.StagePopupIcon(OwnTVIcon.FOLDER) },
                    trailing = {
                        Text(
                            stringResource(R.string.common_number_grouped, folder.count),
                            style = stageText(18, 600),
                            color = StageColors.Muted,
                        )
                    },
                )
            }
        }
    }
}
