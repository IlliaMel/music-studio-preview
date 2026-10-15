package com.illiamelnyk.studio.feature.create.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toClockText
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.core.presentation.track.toUi
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A track of "Your tracks": its row, and what its menu and a failure need besides.
 *
 * @param fix for a prompt the service refused: what to put in the composer instead.
 * @param canRetry the generation failed for another reason, so the same request may work again.
 * @param progress how far composing is, from 0 to 1; `null` while queued, or when not composing.
 */
@Immutable
data class CreateTrackUi(
    val row: TrackUi,
    val isLiked: Boolean,
    val fix: PromptFix? = null,
    val canRetry: Boolean = false,
    val progress: Float? = null,
    val dayLabel: UiText? = null,
    val variation: Int? = null,
    val variationGroupId: String? = null,
)

/**
 * What a refused prompt can become: the service's suggestion, or without one the prompt itself, to
 * rewrite by hand.
 */
@Immutable
data class PromptFix(val prompt: String, val isSuggestion: Boolean)

fun Track.toCreateUi(): CreateTrackUi {
    val error = (status as? TrackStatus.Failed)?.error
    return CreateTrackUi(
        row = toUi().copy(
            subtitle = when (val phase = status) {
                is TrackStatus.Generating -> UiText.Resource(
                    if (phase.progress < 0.5f) R.string.create_creating_title else R.string.create_creating_artwork,
                )

                TrackStatus.Ready -> UiText.Raw(
                    listOf(
                        duration.toClockText(),
                        genres.joinToString(", "),
                    ).filter(String::isNotBlank).joinToString(" \u00b7 "),
                )

                else -> toUi().subtitle
            },
        ),
        isLiked = isLiked,
        fix = (error as? AppError.InvalidPrompt)?.let { PromptFix(it.suggestion ?: prompt, it.suggestion != null) },
        canRetry = error != null && error !is AppError.InvalidPrompt &&
            error != AppError.ProviderOutcomeUnknown && error != AppError.UnsupportedMode &&
            error != AppError.InvalidReference,
        progress = (status as? TrackStatus.Generating)?.progress,
    )
}

/** A music model as its card in the settings shows it. */
@Immutable
data class ModelUi(val model: MusicModel, @param:StringRes val name: Int, @param:StringRes val description: Int)

fun MusicModel.toUi(): ModelUi = when (this) {
    MusicModel.V2_5 -> ModelUi(this, R.string.create_model_v2_5, R.string.create_model_v2_5_text)
    MusicModel.V2 -> ModelUi(this, R.string.create_model_v2, R.string.create_model_v2_text)
}

@get:StringRes
val Quality.label: Int
    get() = when (this) {
        Quality.Auto -> R.string.create_quality_auto
        Quality.Deep -> R.string.create_quality_deep
        Quality.Fast -> R.string.create_quality_fast
    }

/**
 * The composer: the prompt and the options it is sent with.
 *
 * @param lyricsMode whether the engine may add lyrics or must remain instrumental.
 * @param canSend the prompt can be sent now: valid, enough credits, nothing being sent.
 * @param notice why it can't be sent although it is filled in, such as no credits left.
 */
@Immutable
data class ComposerUi(
    val prompt: String = "",
    val quality: Quality = Quality.Auto,
    val lyricsMode: LyricsMode = LyricsMode.Auto,
    val duration: DurationOptionUi? = null,
    val variants: Int = 1,
    val customLyrics: String = "",
    val referenceLabel: String? = null,
    val canSend: Boolean = false,
    val notice: UiText? = null,
    val isSending: Boolean = false,
)

/** The choices of the settings sheet, applied to the composer only with Apply. */
@Immutable
data class SettingsUi(
    val model: MusicModel,
    val lyricsMode: LyricsMode = LyricsMode.Auto,
    val duration: DurationOptionUi?,
    val quality: Quality = Quality.Auto,
    val variants: Int = 1,
)

fun GenerationSettings.toSettingsUi(): SettingsUi = SettingsUi(
    model = model,
    lyricsMode = lyricsMode,
    duration = duration?.toOptionUi(),
    quality = quality,
    variants = variants,
)

/** An idea for a first track, offered while the list is empty: tapping it fills the composer. */
@Immutable
data class PromptIdeaUi(@param:StringRes val title: Int, @param:StringRes val prompt: Int)

val promptIdeas: ImmutableList<PromptIdeaUi> = persistentListOf(
    PromptIdeaUi(R.string.create_idea_rain, R.string.create_idea_rain_prompt),
    PromptIdeaUi(R.string.create_idea_train, R.string.create_idea_train_prompt),
    PromptIdeaUi(R.string.create_idea_sunrise, R.string.create_idea_sunrise_prompt),
    PromptIdeaUi(R.string.create_idea_kitchen, R.string.create_idea_kitchen_prompt),
)

/** The two lists of "Your tracks". */
enum class TracksFilter(@param:StringRes val label: Int) {
    /** The user's own tracks, which only they see. */
    Drafts(R.string.create_drafts),

    /** Tracks on the public profile; coming soon. */
    Published(R.string.create_published),
}
