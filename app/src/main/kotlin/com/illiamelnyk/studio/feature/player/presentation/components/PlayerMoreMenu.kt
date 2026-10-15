package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.ContextMenuItem
import com.illiamelnyk.studio.core.ui.components.StudioContextMenu
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import com.illiamelnyk.studio.feature.player.presentation.SoonFeature

/**
 * "More" with the track's menu: Remix, Add to playlist, Share and Download, as on a track row; all
 * except Remix and Download work locally. Like has its own button under the controls.
 */
@Composable
fun PlayerMoreMenu(
    onShare: () -> Unit,
    onComingSoon: (SoonFeature) -> Unit,
    onAddTo: () -> Unit,
    modifier: Modifier = Modifier,
    canRemix: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    // Every item closes the menu before it does what it says.
    fun pick(action: () -> Unit) {
        expanded = false
        action()
    }
    Box(modifier) {
        StudioIconButton(
            icon = StudioIcons.MoreHoriz,
            contentDescription = stringResource(R.string.player_more),
            onClick = { expanded = true },
            style = StudioIconButtonStyle.OnGlass,
        )
        StudioContextMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canRemix) {
                ContextMenuItem(
                    label = stringResource(R.string.player_remix),
                    onClick = { pick { onComingSoon(SoonFeature.Remix) } },
                    icon = StudioIcons.AutoFixHigh,
                )
            }
            ContextMenuItem(
                label = stringResource(R.string.player_add_to_playlist),
                onClick = { pick(onAddTo) },
                icon = StudioIcons.PlaylistAdd,
            )
            ContextMenuItem(
                label = stringResource(R.string.player_share),
                onClick = { pick(onShare) },
                icon = StudioIcons.IosShare,
            )
            ContextMenuItem(
                label = stringResource(R.string.player_download),
                onClick = { pick { onComingSoon(SoonFeature.Download) } },
                icon = StudioIcons.Download,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerMoreMenuPreview() {
    PlayerPreviewSurface {
        PlayerMoreMenu(onShare = {}, onComingSoon = {}, onAddTo = {})
    }
}
