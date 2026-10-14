package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.LyricsView
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.LyricsUi
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewData
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface

/**
 * The lyrics on cover color: the kit's synced view, which follows [positionMs] through derived
 * state, so it recomposes when the line or the word changes rather than on every tick. Tapping a
 * line seeks to it; scrolling by hand stops following until the Follow along pill is tapped.
 */
@Composable
fun PlayerLyrics(
    lyrics: LyricsUi,
    positionMs: () -> Long,
    onLineClick: (startMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (lyrics) {
        LyricsUi.Loading -> Box(modifier)

        is LyricsUi.Synced -> LyricsView(
            lines = lyrics.lines,
            positionMs = positionMs,
            onSeek = onLineClick,
            modifier = modifier,
        )

        LyricsUi.None -> Box(modifier)
    }
}

private class PlayerLyricsPreviewData : PreviewParameterProvider<LyricsUi> {
    override val values = sequenceOf(PlayerPreviewData.syncedLyrics, LyricsUi.None)
}

@PreviewLightDark
@Composable
private fun PlayerLyricsPreview(@PreviewParameter(PlayerLyricsPreviewData::class) lyrics: LyricsUi) {
    PlayerPreviewSurface {
        PlayerLyrics(lyrics, positionMs = { 17_500L }, onLineClick = {}, modifier = Modifier.fillMaxSize())
    }
}
