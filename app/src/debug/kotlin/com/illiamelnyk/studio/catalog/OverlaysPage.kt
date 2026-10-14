package com.illiamelnyk.studio.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.illiamelnyk.studio.core.ui.components.ContextMenuDivider
import com.illiamelnyk.studio.core.ui.components.ContextMenuItem
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioConfirmDialog
import com.illiamelnyk.studio.core.ui.components.StudioContextMenu
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioToast
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.core.ui.components.TrackRow
import com.illiamelnyk.studio.core.ui.icons.StudioIcons

/** The sheet (hosted by the catalog's root, over everything), the context menu, the dialog and toasts. */
@Composable
internal fun OverlaysPage(onOpenSheet: () -> Unit) {
    CatalogPageList {
        item {
            Section("BottomSheet") {
                StudioButton("Voice to Song · coming soon", onClick = onOpenSheet, style = StudioButtonStyle.Secondary)
            }
        }
        item { Section("ContextMenu") { MenuOnRow() } }
        item { Section("ConfirmDialog") { DeleteWithConfirmation() } }
        item { Section("Toast") { Toasts() } }
    }
}

@Composable
private fun ColumnScope.MenuOnRow() {
    // Each menu shares a parent with its trigger, which it anchors to: the row, or the button.
    var rowMenuOpen by remember { mutableStateOf(false) }
    Box {
        TrackRow(
            title = "Rust and Redemption",
            subtitle = "Enrique Flores",
            coverSeed = "Rust and Redemption",
            onClick = {},
            onMore = { rowMenuOpen = true },
        )
        TrackMenu(expanded = rowMenuOpen, onDismissRequest = { rowMenuOpen = false })
    }
    var buttonMenuOpen by remember { mutableStateOf(false) }
    Box {
        StudioIconButton(
            icon = StudioIcons.MoreHoriz,
            contentDescription = "More",
            onClick = { buttonMenuOpen = true },
            style = StudioIconButtonStyle.Surface,
        )
        TrackMenu(expanded = buttonMenuOpen, onDismissRequest = { buttonMenuOpen = false })
    }
}

@Composable
private fun TrackMenu(expanded: Boolean, onDismissRequest: () -> Unit) {
    StudioContextMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        ContextMenuItem("Remix", onClick = onDismissRequest, icon = StudioIcons.AutoFixHigh, hint = "Soon")
        ContextMenuItem("Play next", onClick = onDismissRequest, icon = StudioIcons.PlaylistPlay)
        ContextMenuItem("Like", onClick = onDismissRequest, icon = StudioIcons.Favorite)
        ContextMenuItem("Add to playlist", onClick = onDismissRequest, icon = StudioIcons.PlaylistAdd)
        ContextMenuItem("Share", onClick = onDismissRequest, icon = StudioIcons.IosShare)
        ContextMenuItem("Download", onClick = onDismissRequest, icon = StudioIcons.Download, hint = "5 left")
        ContextMenuDivider()
        ContextMenuItem("Delete", onClick = onDismissRequest, icon = StudioIcons.Delete, danger = true)
    }
}

@Composable
private fun ColumnScope.DeleteWithConfirmation() {
    var asking by remember { mutableStateOf(false) }
    StudioButton("Delete track", onClick = { asking = true }, style = StudioButtonStyle.Danger)
    if (asking) {
        StudioConfirmDialog(
            title = "Delete this track?",
            message = "It used credits and can't be restored.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onConfirm = { asking = false },
            onDismissRequest = { asking = false },
        )
    }
}

@Composable
private fun ColumnScope.Toasts() {
    StudioToast(
        title = "Error",
        message = "You can't remix a track that's already a remix",
        kind = StudioToastKind.Error,
    )
    StudioToast(title = "Your track is ready", message = "Lo-fi rainy morning · 1:30")
}
