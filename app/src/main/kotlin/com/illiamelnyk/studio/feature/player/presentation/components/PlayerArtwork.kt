package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.min
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.LyricsUi
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewData
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import com.illiamelnyk.studio.feature.player.presentation.host.playerCoverShape

/**
 * The big square cover, as large as the space allows, or the lyrics in its place; they cross-fade.
 *
 * @param positionMs read by the lyrics only, through derived state: see [PlayerLyrics].
 * @param coverModifier makes the cover the shared element that travels from the mini player.
 */
@Composable
fun PlayerArtwork(
    coverSeed: String,
    showLyrics: Boolean,
    lyrics: LyricsUi,
    positionMs: () -> Long,
    onLyricLineClick: (startMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    trackKey: String = coverSeed,
) {
    val motion = StudioTheme.motion
    AnimatedContent(
        targetState = showLyrics && lyrics is LyricsUi.Synced,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(motion.duration.medium)) togetherWith fadeOut(tween(motion.duration.short)) },
        label = "artwork",
    ) { lyricsShown ->
        if (lyricsShown) {
            key(trackKey) { PlayerLyrics(lyrics, positionMs, onLyricLineClick, Modifier.fillMaxSize()) }
        } else {
            PlayerCover(coverSeed, Modifier.fillMaxSize(), coverModifier)
        }
    }
}

@Composable
private fun PlayerCover(seed: String, modifier: Modifier = Modifier, coverModifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val shape = playerCoverShape()
        Cover(
            seed = seed,
            size = min(maxWidth, maxHeight),
            modifier = coverModifier.dropShadow(shape, StudioTheme.effects.elevation.artwork.toShadow()),
            shape = shape,
        )
    }
}

@PreviewLightDark
@Composable
private fun PlayerArtworkPreview() {
    PlayerPreviewSurface {
        PlayerArtwork(
            coverSeed = PlayerPreviewData.glassHarbor.coverSeed,
            showLyrics = false,
            lyrics = LyricsUi.Loading,
            positionMs = { 0L },
            onLyricLineClick = {},
        )
    }
}
