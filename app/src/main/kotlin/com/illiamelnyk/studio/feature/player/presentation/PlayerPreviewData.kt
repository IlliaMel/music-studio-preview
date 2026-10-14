package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.ui.components.LyricLine
import com.illiamelnyk.studio.core.ui.components.LyricWord
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.components.PlayerBackdrop
import kotlinx.collections.immutable.toImmutableList

/** Tracks, lyrics and queues for the player's previews. */
internal object PlayerPreviewData {
    val glassHarbor = NowPlayingUi(TrackId("glass-harbor"), "Glass Harbor", "Mira Vale", "Glass Harbor")
    private val coldBrew =
        NowPlayingUi(TrackId("cold-brew"), "Cold Brew Mornings", "Hollow Pines", "Cold Brew Mornings")
    private val lofiStation = NowPlayingUi(TrackId("static-bloom"), "Static Bloom", "Taras Hai", "Static Bloom")

    val syncedLyrics = LyricsUi.Synced(
        listOf(
            "Lights are low on the glass harbor" to 12_000L,
            "Boats asleep in the evening haze" to 16_500L,
            "I keep your voice in a paper lantern" to 21_000L,
            "Burning slow through the winter days" to 25_500L,
        ).map { (text, start) ->
            val words = text.split(' ')
            LyricLine(start, words.mapIndexed { index, word -> LyricWord(word, start + index * WORD_MS) })
        }.toImmutableList(),
    )

    val upNext = listOf(coldBrew, lofiStation).map { track ->
        TrackUi(track.id, track.title, UiText.Raw(track.subtitle), track.coverSeed, TrackUi.Status.Ready)
    }.toImmutableList()

    private const val WORD_MS = 450L

    /** Playing with the cover; the lyrics of an instrumental; a paused station; nothing. */
    class States : PreviewParameterProvider<PlayerContent> {
        override val values = sequenceOf(
            PlayerContent(
                track = glassHarbor,
                isPlaying = true,
                lyrics = syncedLyrics,
                upNext = upNext,
                shuffle = true,
            ),
            PlayerContent(
                track = coldBrew,
                isLiked = true,
                repeat = true,
                lyrics = LyricsUi.None,
                showLyrics = true,
            ),
            PlayerContent(track = lofiStation, isPlaying = true, isStation = true, lyrics = LyricsUi.Loading),
            PlayerContent(
                track = glassHarbor,
                lyrics = syncedLyrics,
                upNext = upNext,
                sheet = PlayerSheet.Queue,
            ),
            PlayerContent(track = coldBrew, sheet = PlayerSheet.ComingSoon(SoonFeature.Remix)),
        )
    }

    val clock = PlaybackClock(positionMs = 17_500, durationMs = 192_000)
}

/**
 * The player's sections in a preview: the dark scheme the player always uses, over the cover
 * background, one under the other.
 */
@Composable
internal fun PlayerPreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    StudioTheme(darkTheme = true) {
        PlayerBackdrop(PlayerPreviewData.glassHarbor.coverSeed) {
            Column(verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.m), content = content)
        }
    }
}
