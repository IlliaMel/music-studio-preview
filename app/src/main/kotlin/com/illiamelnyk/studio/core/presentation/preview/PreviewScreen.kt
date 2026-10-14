package com.illiamelnyk.studio.core.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The theme and the screen background around a whole screen in a preview. Previews use
 * `@PreviewLightDark`, so the scheme follows the preview's night mode.
 */
@Composable
internal fun PreviewScreen(content: @Composable () -> Unit) {
    StudioTheme {
        Box(Modifier.background(StudioTheme.colors.background)) { content() }
    }
}
