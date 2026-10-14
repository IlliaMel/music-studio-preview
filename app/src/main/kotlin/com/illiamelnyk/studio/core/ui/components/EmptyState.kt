package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * What a screen shows when it has nothing to show yet: a 32 dp icon, a title, a message and an
 * optional [action], centered. TalkBack reads the texts as one item.
 */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StudioIcon(icon, contentDescription = null, tint = colors.content.secondary, size = dimens.size.icon.xl)
        StudioText(title, StudioTheme.typography.titleMedium, textAlign = TextAlign.Center)
        StudioText(
            text = message,
            style = StudioTheme.typography.bodyMedium,
            color = colors.content.secondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = dimens.spacing.xs)) { action() }
        }
    }
}

@PreviewLightDark
@Composable
private fun EmptyStatePreview() {
    PreviewSurface {
        EmptyState(
            icon = StudioIcons.AutoAwesome,
            title = "Create with Studio",
            message = "Transform your ideas into full tracks.",
        ) {
            StudioButton("Create a track", onClick = {})
        }
    }
}
