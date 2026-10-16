package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.ContextMenuDivider
import com.illiamelnyk.studio.core.ui.components.ContextMenuItem
import com.illiamelnyk.studio.core.ui.components.StudioContextMenu
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.feature.create.presentation.TrackMenuAction

/**
 * The MVP menu of one of the user's tracks; audio export is available and Delete asks first.
 */
// The root is the menu's popup window; the modifier is for the menu inside it.
@Suppress("ktlint:compose:modifier-not-used-at-root")
@Composable
fun TrackMenu(
    expanded: Boolean,
    onAction: (TrackMenuAction) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    canUseAsReference: Boolean = false,
) {
    StudioContextMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        ContextMenuItem(
            label = stringResource(R.string.create_menu_play_next),
            onClick = { onAction(TrackMenuAction.PlayNext) },
            icon = StudioIcons.PlaylistPlay,
        )
        ContextMenuItem(
            label = stringResource(R.string.create_menu_share),
            onClick = { onAction(TrackMenuAction.Share) },
            icon = StudioIcons.IosShare,
        )
        ContextMenuItem(
            label = stringResource(R.string.create_menu_download),
            onClick = { onAction(TrackMenuAction.Download) },
            icon = StudioIcons.Download,
        )
        ContextMenuItem(
            label = stringResource(R.string.create_menu_rename),
            onClick = { onAction(TrackMenuAction.Rename) },
            icon = StudioIcons.Edit,
        )
        ContextMenuItem(
            label = stringResource(R.string.create_menu_use_prompt),
            onClick = { onAction(TrackMenuAction.UsePrompt) },
            icon = StudioIcons.Notes,
        )
        if (canUseAsReference) {
            ContextMenuItem(
                label = stringResource(R.string.create_menu_use_as_reference),
                onClick = { onAction(TrackMenuAction.UseAsReference) },
                icon = StudioIcons.Notes,
            )
        }
        ContextMenuDivider()
        ContextMenuItem(
            label = stringResource(R.string.create_menu_delete),
            onClick = { onAction(TrackMenuAction.Delete) },
            icon = StudioIcons.Delete,
            danger = true,
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackMenuPreview() {
    PreviewScreen {
        Box {
            StudioIconButton(StudioIcons.MoreHoriz, contentDescription = "", onClick = {})
            TrackMenu(expanded = true, onAction = {}, onDismissRequest = {})
        }
    }
}
