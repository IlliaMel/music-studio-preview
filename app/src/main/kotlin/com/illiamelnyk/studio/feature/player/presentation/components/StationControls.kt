package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/** LIVE status, a static timeline and stop: stations cannot seek or skip. */
@Composable
fun StationControls(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val stopDescription = stringResource(R.string.player_stop)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacing.s)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = dimens.size.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        ) {
            StudioText(
                text = buildAnnotatedString {
                    val label = stringResource(R.string.player_live)
                    withStyle(SpanStyle(color = colors.error.content)) { append(label.take(1)) }
                    append(label.drop(1))
                },
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
                    .minimumTouchTarget()
                    .clickable(role = Role.Button, onClick = onStop)
                    .semantics { contentDescription = stopDescription }
                    .size(dimens.size.component.playButton),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(dimens.size.component.iconButton)
                        .background(colors.overlay.onMediaPrimary, StudioTheme.shapes.xs),
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
