package com.illiamelnyk.studio.feature.player.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.Track

/** The playing track as the players show it. */
@Immutable
data class NowPlayingUi(val title: String, val artist: String, val coverSeed: String)

fun Track.toNowPlaying(): NowPlayingUi = NowPlayingUi(title = title, artist = artist, coverSeed = coverSeed.value)
