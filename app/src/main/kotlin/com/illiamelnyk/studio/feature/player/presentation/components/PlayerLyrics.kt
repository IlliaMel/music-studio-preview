package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The line being sung and the one after it. It keeps its height when there are no lyrics, so the
 * controls below don't jump between tracks.
 */
@Composable
fun PlayerLyrics(line: String?, nextLine: String?, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.size.cover.l / 2)
            .padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (line != null) {
            StudioText(line, StudioTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
        if (nextLine != null) {
            StudioText(
                text = nextLine,
                style = StudioTheme.typography.bodyMedium,
                color = StudioTheme.colors.content.tertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlayerLyricsPreview() {
    PreviewScreen {
        PlayerLyrics(line = "Boats asleep in the evening haze", nextLine = "I keep your voice in a paper lantern")
    }
}
