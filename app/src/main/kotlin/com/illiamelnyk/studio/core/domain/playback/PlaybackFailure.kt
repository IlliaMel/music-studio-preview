package com.illiamelnyk.studio.core.domain.playback

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.TrackId

data class PlaybackFailure(val id: Long, val trackId: TrackId, val error: AppError)
