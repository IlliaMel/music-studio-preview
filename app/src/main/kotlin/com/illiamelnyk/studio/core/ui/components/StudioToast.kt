package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

enum class StudioToastKind {
    /** Something finished: "Your track is ready". */
    Info,

    /** Generation or the network failed. */
    Error,
}

/**
 * A message that floats at the top of the screen: an icon, a title and a line of detail. This is
 * only how it looks; showing, queueing and hiding toasts is the job of a host that comes later.
 * The border separates it from the content below. The kit's drop shadow has no token yet and is too
 * heavy on the light scheme, so it waits for an elevation token.
 *
 * TalkBack announces it when it appears.
 */
@Composable
fun StudioToast(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    kind: StudioToastKind = StudioToastKind.Info,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.l
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .fillMaxWidth()
            .background(colors.surface.level3, shape)
            .border(dimens.size.component.divider, colors.outline.subtle, shape)
            .padding(horizontal = dimens.spacing.m, vertical = dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        when (kind) {
            StudioToastKind.Info -> StudioIcon(StudioIcons.CheckCircleFilled, contentDescription = null)

            StudioToastKind.Error -> StudioIcon(
                StudioIcons.ErrorFilled,
                contentDescription = null,
                tint = colors.error.content,
            )
        }
        Column {
            StudioText(title, StudioTheme.typography.titleSmall)
            StudioText(message, StudioTheme.typography.bodySmall, color = colors.content.secondary)
        }
    }
}

@PreviewLightDark
@Composable
private fun StudioToastPreview() {
    PreviewSurface {
        StudioToast(
            title = "Error",
            message = "You can't remix a track that's already a remix",
            kind = StudioToastKind.Error,
        )
        StudioToast(title = "Your track is ready", message = "Lo-fi rainy morning · 1:30")
    }
}
