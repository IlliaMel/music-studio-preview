package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A 56 dp row of a sound, such as a sample to use as a reference: a play button drawn over the
 * sound's art, the title and a subtitle, and an optional [trailing] action such as "like".
 */
@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped container.
@Composable
fun SoundRow(
    title: String,
    subtitle: String,
    coverSeed: String,
    playing: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val dimens = StudioTheme.dimens
    val playDescription = stringResource(if (playing) R.string.ui_pause_named else R.string.ui_play_named, title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.listRow),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .minimumTouchTarget()
                .clickable(role = Role.Button, onClick = onPlayClick)
                .semantics { contentDescription = playDescription }
                .size(dimens.size.component.iconButton)
                .clip(StudioTheme.shapes.full)
                .coverArt(coverSeed),
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(
                icon = if (playing) StudioIcons.PauseFilled else StudioIcons.PlayArrowFilled,
                contentDescription = null,
                tint = StudioTheme.colors.overlay.onMediaPrimary,
            )
        }
        Column(Modifier.weight(1f)) {
            StudioText(title, StudioTheme.typography.titleSmall, maxLines = 1)
            StudioText(
                text = subtitle,
                style = StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.content.secondary,
                maxLines = 1,
            )
        }
        trailing?.invoke()
    }
}

@PreviewLightDark
@Composable
private fun SoundRowPreview() {
    PreviewSurface {
        SoundRow(
            title = "Rally Bassground",
            subtitle = "808 Bass · 101 BPM · C# Minor",
            coverSeed = "Rally Bassground",
            playing = false,
            onPlayClick = {},
        ) {
            StudioIconButton(StudioIcons.Favorite, "Like", onClick = {}, style = StudioIconButtonStyle.Muted)
        }
    }
}
