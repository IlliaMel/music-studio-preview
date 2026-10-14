package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import kotlinx.collections.immutable.persistentListOf

/** Create in each of its states: tracks in every status, no tracks yet, loading, a failed action. */
class CreatePreviewData : PreviewParameterProvider<CreateState> {
    private val tracks = persistentListOf(
        TrackUi(
            id = TrackId("1"),
            title = "Night train synth-pop",
            subtitle = UiText.Resource(R.string.track_composing, 40),
            coverSeed = "Night train synth-pop",
            status = TrackUi.Status.Generating,
        ),
        TrackUi(
            id = TrackId("2"),
            title = "Rainy window lo-fi",
            subtitle = UiText.Raw("Illia Melnyk · 2:00"),
            coverSeed = "Rainy window lo-fi",
            status = TrackUi.Status.Ready,
        ),
        TrackUi(
            id = TrackId("3"),
            title = "Song in the style of a famous band",
            subtitle = UiText.Raw("Illia Melnyk · 2:00"),
            coverSeed = "Song in the style of a famous band",
            status = TrackUi.Status.Failed(
                UiText.Resource(R.string.error_invalid_prompt_suggestion, "Indie rock with an original sound"),
            ),
        ),
    )

    override val values = sequenceOf(
        CreateState(tracks = tracks, playingTrackId = TrackId("2"), isLoading = false),
        CreateState(isLoading = false),
        CreateState(isLoading = true),
        CreateState(tracks = tracks, isLoading = false, notice = UiText.Resource(R.string.error_quota_exceeded)),
    )
}
