package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.NowPlayingUi

/** The cover, the title and the artist, centered in the space it gets. */
@Composable
fun PlayerArtwork(track: NowPlayingUi, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(seed = track.coverSeed, size = dimens.size.cover.l, shape = StudioTheme.shapes.l)
        StudioText(
            text = track.title,
            style = StudioTheme.typography.titleLarge,
            modifier = Modifier.padding(top = dimens.spacing.m),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        StudioText(track.artist, StudioTheme.typography.bodyMedium, color = StudioTheme.colors.content.secondary)
    }
}

@PreviewLightDark
@Composable
private fun PlayerArtworkPreview() {
    PreviewScreen {
        PlayerArtwork(NowPlayingUi(title = "Glass Harbor", artist = "Mira Vale", coverSeed = "Glass Harbor"))
    }
}
