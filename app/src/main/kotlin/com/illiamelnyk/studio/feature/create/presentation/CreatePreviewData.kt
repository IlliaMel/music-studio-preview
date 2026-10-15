package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.domain.Prompt
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Create in each of its states: tracks in every status, no tracks yet, loading, published, the
 * composer expanded with options, and out of credits.
 */
class CreatePreviewData : PreviewParameterProvider<CreateState> {
    override val values = sequenceOf(
        CreateState(tracks = tracks, playingTrackId = TrackId("2"), isLoading = false),
        CreateState(isLoading = false),
        CreateState(isLoading = true),
        CreateState(filter = TracksFilter.Published, tracks = tracks, isLoading = false),
        CreateState(tracks = tracks, isLoading = false, composer = composers[1]),
        CreateState(tracks = tracks, isLoading = false, composer = composers[3]),
    )

    companion object {
        val tracks: ImmutableList<CreateTrackUi> = persistentListOf(
            CreateTrackUi(
                row = TrackUi(
                    id = TrackId("1"),
                    title = "Night train synth-pop",
                    subtitle = UiText.Resource(R.string.track_composing, 40),
                    coverSeed = "Night train synth-pop",
                    status = TrackUi.Status.Generating,
                ),
                isLiked = false,
                progress = 0.4f,
            ),
            CreateTrackUi(
                row = TrackUi(
                    id = TrackId("2"),
                    title = "Rainy window lo-fi",
                    subtitle = UiText.Raw("Illia Melnyk · 2:00"),
                    coverSeed = "Rainy window lo-fi",
                    status = TrackUi.Status.Ready,
                ),
                isLiked = true,
            ),
            CreateTrackUi(
                row = TrackUi(
                    id = TrackId("3"),
                    title = "Song in the style of a famous band",
                    subtitle = UiText.Raw("Illia Melnyk · 2:00"),
                    coverSeed = "Song in the style of a famous band",
                    status = TrackUi.Status.Failed(
                        UiText.Resource(R.string.error_invalid_prompt_suggestion, SUGGESTION),
                    ),
                ),
                isLiked = false,
                fix = PromptFix(SUGGESTION, isSuggestion = true),
            ),
            CreateTrackUi(
                row = TrackUi(
                    id = TrackId("4"),
                    title = "Morning coffee jazz",
                    subtitle = UiText.Raw("Illia Melnyk · 1:30"),
                    coverSeed = "Morning coffee jazz",
                    status = TrackUi.Status.Failed(UiText.Resource(R.string.error_server)),
                ),
                isLiked = false,
                canRetry = true,
            ),
        )

        /** Collapsed and empty; expanded with options; over the limit; out of credits. */
        val composers: ImmutableList<ComposerUi> = persistentListOf(
            ComposerUi(),
            ComposerUi(
                prompt = "A slow lo-fi beat for a rainy morning, with soft piano",
                quality = Quality.Deep,
                duration = 1.minutes.toOptionUi(),
                variants = 2,
                canSend = true,
            ),
            ComposerUi(prompt = "la ".repeat(Prompt.MAX_LENGTH / 3 + 1)),
            ComposerUi(
                prompt = "Dreamy synth-pop about a night train",
                notice = UiText.Resource(R.string.error_quota_exceeded),
            ),
        )

        val settingsDraft = SettingsUi(
            model = MusicModel.V2_5,
            lyricsMode = LyricsMode.Instrumental,
            duration = 3.minutes.toOptionUi(),
        )

        private const val SUGGESTION = "Indie rock song with an original sound of its own"
    }
}
