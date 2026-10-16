package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.TrackId

data class PlaybackMetadataSnapshot(val ownerId: String, val tracks: List<PlaybackTrackMetadata>)

data class PlaybackTrackMetadata(val id: TrackId, val title: String, val artist: String, val coverSeed: CoverSeed)
