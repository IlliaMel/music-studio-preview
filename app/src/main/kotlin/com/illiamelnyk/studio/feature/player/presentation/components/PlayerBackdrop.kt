package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.components.coverBackdrop
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The player's background: the colors of the cover of [coverSeed], shaded into black, the kit's
 * media background, which needs no bitmap. It cross-fades when the track changes.
 *
 * The glass in [content] gets no backdrop to blur, so it shows the kit's plain glass tint: blurring
 * this smooth gradient changes nothing visible, and on the emulator it cost the slide of the player
 * over 20 ms a frame.
 */
@Composable
fun PlayerBackdrop(coverSeed: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Crossfade(
            targetState = coverSeed,
            modifier = Modifier.matchParentSize(),
            animationSpec = tween(StudioTheme.motion.duration.long),
            label = "playerBackdrop",
        ) { seed ->
            Box(Modifier.fillMaxSize().coverBackdrop(seed))
        }
        content()
    }
}

@PreviewLightDark
@Composable
private fun PlayerBackdropPreview() {
    StudioTheme {
        PlayerBackdrop("Glass Harbor") {}
    }
}
