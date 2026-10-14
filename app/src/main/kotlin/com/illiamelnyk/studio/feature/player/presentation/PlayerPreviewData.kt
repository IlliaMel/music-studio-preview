package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.core.domain.playback.RepeatMode

/** The player with lyrics, an instrumental paused on repeat, and nothing loaded. */
class PlayerPreviewData : PreviewParameterProvider<PlayerState> {
    override val values = sequenceOf(
        PlayerState(
            track = NowPlayingUi(title = "Glass Harbor", artist = "Mira Vale", coverSeed = "Glass Harbor"),
            isPlaying = true,
            position = "0:17",
            duration = "3:12",
            lyricLine = "Boats asleep in the evening haze",
            nextLyricLine = "I keep your voice in a paper lantern",
        ),
        PlayerState(
            track = NowPlayingUi(
                title = "Cold Brew Mornings",
                artist = "Hollow Pines",
                coverSeed = "Cold Brew Mornings",
            ),
            position = "1:02",
            duration = "2:05",
            shuffle = true,
            repeat = RepeatMode.One,
        ),
        PlayerState(),
    )
}
