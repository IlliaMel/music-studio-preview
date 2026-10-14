package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/** LIVE status, a static timeline and stop: stations cannot seek or skip. */
@Composable
fun StationControls(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacing.s)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = dimens.size.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        ) {
            StudioText(
                text = stringResource(R.string.player_live),
                style = StudioTheme.typography.bodySmall,
                color = colors.overlay.onMediaPrimary,
            )
            Box(
                Modifier.weight(1f).height(dimens.spacing.xxs)
                    .background(colors.overlay.onMediaSecondary, StudioTheme.shapes.full),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onStop)
                    .size(dimens.size.component.playButton)
                    .background(colors.overlay.onMediaPrimary, StudioTheme.shapes.full),
                contentAlignment = Alignment.Center,
            ) {
                StudioIcon(
                    icon = StudioIcons.StopFilled,
                    contentDescription = stringResource(R.string.player_stop),
                    tint = colors.inverse.content,
                    size = dimens.size.icon.xl,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun StationControlsPreview() {
    PlayerPreviewSurface {
        StationControls(onStop = {})
    }
}
