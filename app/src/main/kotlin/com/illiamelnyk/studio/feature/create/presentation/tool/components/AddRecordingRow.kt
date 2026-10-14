package com.illiamelnyk.studio.feature.create.presentation.tool.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.StudioBadge
import com.illiamelnyk.studio.core.ui.components.StudioBadgeStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.Recording
import com.illiamelnyk.studio.feature.create.presentation.tool.RecordingUi
import com.illiamelnyk.studio.feature.create.presentation.tool.toUi

/**
 * "Add your voice" (or sample) of a tool that works on the user's audio: a microphone, what to
 * add and why, and a plus. Recording isn't there yet, so it is marked as coming soon and opens the
 * sheet that explains it.
 */
@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped card.
@Composable
fun AddRecordingRow(recording: RecordingUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.l
    Row(
        modifier = modifier
            .padding(horizontal = dimens.layout.screenPadding)
            .clickable(
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.Button,
                onClick = onClick,
            )
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.level3)
            .padding(dimens.layout.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimens.size.avatar.m)
                .background(colors.surface.level4, StudioTheme.shapes.full),
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(StudioIcons.Mic, contentDescription = null, size = dimens.size.icon.m)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioText(
                    stringResource(recording.title),
                    StudioTheme.typography.titleSmall,
                    Modifier.weight(1f, false),
                )
                StudioBadge(stringResource(R.string.ui_soon), style = StudioBadgeStyle.Soon)
            }
            StudioText(
                stringResource(recording.message),
                StudioTheme.typography.bodySmall,
                color = colors.content.secondary,
            )
        }
        StudioIcon(StudioIcons.Add, contentDescription = null, tint = colors.content.secondary)
    }
}

@PreviewLightDark
@Composable
private fun AddRecordingRowPreview() {
    PreviewScreen { AddRecordingRow(recording = Recording.Voice.toUi(), onClick = {}) }
}
