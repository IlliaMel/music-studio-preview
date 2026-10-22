package com.illiamelnyk.studio.core.presentation.track

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toClockText
import com.illiamelnyk.studio.core.presentation.toUiText

/** A track as a row of a list shows it: every text already chosen. */
@Immutable
data class TrackUi(
    val id: TrackId,
    val title: String,
    val subtitle: UiText,
    val coverSeed: String,
    val status: Status,
) {
    @Immutable
    sealed interface Status {
        data object Ready : Status

        /** Queued or composing: the cover is alive and a progress bar runs. */
        data object Generating : Status

        data class Failed(val message: UiText) : Status
    }
}

fun Track.toUi(): TrackUi = TrackUi(
    id = id,
    title = title,
    subtitle = when (val status = status) {
        TrackStatus.Queued -> UiText.Resource(R.string.track_queued)

        is TrackStatus.Generating -> UiText.Resource(R.string.track_composing_status)

        TrackStatus.Ready, is TrackStatus.Failed -> UiText.Resource(
            R.string.track_subtitle,
            artist,
            duration.toClockText(),
        )
    },
    coverSeed = coverSeed.value,
    status = when (val status = status) {
        TrackStatus.Queued, is TrackStatus.Generating -> TrackUi.Status.Generating
        TrackStatus.Ready -> TrackUi.Status.Ready
        is TrackStatus.Failed -> TrackUi.Status.Failed(status.error.toUiText())
    },
)
