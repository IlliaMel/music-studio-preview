package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The theme and the screen background around the components of a preview, stacked like in the kit.
 * Previews use `@PreviewLightDark`, so the theme follows the preview's night mode.
 */
@Composable
internal fun PreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    StudioTheme {
        Column(
            modifier = Modifier
                .background(StudioTheme.colors.background)
                .padding(StudioTheme.dimens.spacing.m),
            verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
            content = content,
        )
    }
}
