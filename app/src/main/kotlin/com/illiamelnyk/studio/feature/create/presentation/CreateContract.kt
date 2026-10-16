package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Quality
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.presentation.track.TrackUi
import com.illiamelnyk.studio.feature.create.domain.Tool
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.DurationOptionUi
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.trackLengthOptions
import kotlin.time.Duration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The Create tab: shortcuts, tools, the user's tracks and the composer that makes new ones.
 *
 * @param playingTrackId the track the player has loaded, marked in the list.
 * @param sheet the sheet that is open, if any.
 * @param menuTrackId the track whose menu is open.
 * @param deleting the track the user is asked to confirm deleting.
 */
@Immutable
data class CreateState(
    val tools: ImmutableList<ToolUi> = Tool.entries.filter(Tool::featured).map(Tool::toUi).toImmutableList(),
    val filter: TracksFilter = TracksFilter.Drafts,
    val tracks: ImmutableList<CreateTrackUi> = persistentListOf(),
    val playingTrackId: TrackId? = null,
    val isLoading: Boolean = true,
    val ideas: ImmutableList<PromptIdeaUi> = promptIdeas,
    val composer: ComposerUi = ComposerUi(),
    val models: ImmutableList<ModelUi> = MusicModel.entries.map(MusicModel::toUi).toImmutableList(),
    val durations: ImmutableList<DurationOptionUi> = trackLengthOptions,
    val sheet: CreateSheet? = null,
    val menuTrackId: TrackId? = null,
    val deleting: TrackUi? = null,
)

/** The sheets of Create; one is open at a time. */
@Immutable
sealed interface CreateSheet {
    data object Notify : CreateSheet

    data class Quota(val dailyLimit: Int) : CreateSheet

    data object Lyrics : CreateSheet

    data object Reference : CreateSheet

    data class Rename(val id: TrackId, val title: String) : CreateSheet

    /** The settings, with the choices made in the sheet so far. */
    data class Settings(val draft: SettingsUi) : CreateSheet

    data class ComingSoon(val feature: ComingSoonFeature) : CreateSheet
}

/** What the menu of a track can do. */
enum class TrackMenuAction { PlayNext, Share, Download, Rename, UsePrompt, UseAsReference, Delete }

sealed interface CreateIntent {
    data class ComingSoonClicked(val feature: ComingSoonFeature) : CreateIntent

    data object StylesClicked : CreateIntent

    data object ToolsClicked : CreateIntent

    data class ToolClicked(val tool: Tool) : CreateIntent

    data class FilterSelected(val filter: TracksFilter) : CreateIntent

    /** A prompt idea of the empty list; [prompt] goes into the composer. */
    data class IdeaClicked(val prompt: String) : CreateIntent

    data class TrackClicked(val id: TrackId) : CreateIntent

    data class RetryClicked(val id: TrackId) : CreateIntent

    /** "Use suggestion" or "Edit prompt" under a track whose prompt was refused. */
    data class FixClicked(val id: TrackId) : CreateIntent

    /** Delete under a failed track; finished ones delete from their menu. Only Ready asks first. */
    data class DeleteClicked(val id: TrackId) : CreateIntent

    data class MoreClicked(val id: TrackId) : CreateIntent

    data object MenuDismissed : CreateIntent

    /** An item of the open menu, for the track [CreateState.menuTrackId]. */
    data class MenuActionClicked(val action: TrackMenuAction) : CreateIntent

    data object DeleteConfirmed : CreateIntent

    data object DeleteDismissed : CreateIntent

    data class PromptChanged(val text: String) : CreateIntent

    data object SendClicked : CreateIntent

    data object QuotaClicked : CreateIntent

    data class NotificationAnswered(val notify: Boolean) : CreateIntent

    data class QualitySelected(val quality: Quality) : CreateIntent

    data object InstrumentalRemoved : CreateIntent

    data object LyricsClicked : CreateIntent

    data object CustomLyricsClicked : CreateIntent

    data class CustomLyricsChanged(val text: String) : CreateIntent

    data object ReferenceClicked : CreateIntent

    data class ReferenceSelected(val uri: String, val title: String) : CreateIntent

    data object ReferenceRemoved : CreateIntent

    data class RemixSourceRemoved(val id: TrackId) : CreateIntent

    data class RemixSourceClicked(val id: TrackId) : CreateIntent

    data class RecordingSaved(val uri: String, val title: String, val durationMs: Long) : CreateIntent

    data class LyricsSelected(val instrumental: Boolean) : CreateIntent

    data class DurationSelected(val duration: Duration?) : CreateIntent

    data class RenameChanged(val title: String) : CreateIntent

    data object RenameConfirmed : CreateIntent

    /** Switches between one and two tracks per prompt. */
    data object VariantsClicked : CreateIntent

    data object SettingsClicked : CreateIntent

    data class SettingsQualitySelected(val quality: Quality) : CreateIntent

    data class SettingsVariantsSelected(val variants: Int) : CreateIntent

    data class SettingsModelSelected(val model: MusicModel) : CreateIntent

    data object SettingsCustomLyricsSelected : CreateIntent

    data class SettingsInstrumentalChanged(val instrumental: Boolean) : CreateIntent

    data class SettingsDurationSelected(val duration: Duration?) : CreateIntent

    data object SettingsReset : CreateIntent

    data object SettingsApplied : CreateIntent

    /** "I want this" on a coming soon sheet. */
    data object WantClicked : CreateIntent

    data object SheetDismissed : CreateIntent
}

sealed interface CreateEffect {
    data object RequestNotifications : CreateEffect

    data object OpenTools : CreateEffect

    data object OpenPlayer : CreateEffect

    data class OpenTool(val tool: Tool) : CreateEffect

    data class ShowToast(val toast: ToastUi) : CreateEffect

    /** Shares a track as text, until there are audio files to share. */
    data class ShareTrack(
        val title: String,
        val prompt: String,
        val track: com.illiamelnyk.studio.core.domain.track.Track? = null,
    ) : CreateEffect

    /** New tracks were started: they are at the top of the list, and the composer is done. */
    data class DownloadAudio(val track: com.illiamelnyk.studio.core.domain.track.Track) : CreateEffect

    data object TracksStarted : CreateEffect
}
