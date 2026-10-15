package com.illiamelnyk.studio.feature.create.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toClockText
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import com.illiamelnyk.studio.feature.create.domain.GenerateTracks
import com.illiamelnyk.studio.feature.create.domain.GenerationOutcome
import com.illiamelnyk.studio.feature.create.domain.GenerationSettings
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.domain.NotificationChoice
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.feature.create.domain.TrackRenames
import com.illiamelnyk.studio.feature.create.domain.endedGenerations
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CreateViewModel(
    private val tracks: TrackRepository,
    credits: CreditsRepository,
    private val playback: PlaybackController,
    private val generateTracks: GenerateTracks,
    private val playNext: PlayNext,
    private val clock: Clock,
    private val notificationChoice: NotificationChoice,
    private val renames: TrackRenames,
    private val drafts: ComposerDraftStore = ComposerDraftStore(),
) : MviViewModel<CreateState, CreateIntent, CreateEffect>(CreateState()) {

    /** The tracks as the repository last sent them: to play, to share, and to see generations end. */
    private var myTracks: List<Track> = emptyList()

    /** What the composer generates with; the composer's state shows it. */
    private var settings = GenerationSettings()

    /** `null` until the credits are known. */
    private var creditsLeft: Int? = null
    private var dailyLimit = 0

    init {
        tracks.observeMine().onEach(::showTracks).launchIn(viewModelScope)
        drafts.pending.onEach { track ->
            if (track != null) {
                changeSettings { copy(remixTrackId = track.id.value) }
                updateComposer { copy(prompt = track.prompt, referenceLabel = track.title) }
                drafts.clear()
            }
        }.launchIn(viewModelScope)
        playback.state.map { it.current?.id }
            .distinctUntilChanged()
            .onEach { id -> setState { copy(playingTrackId = id) } }
            .launchIn(viewModelScope)
        credits.credits.onEach { credits ->
            val exhausted = creditsLeft != null && creditsLeft != 0 && credits.remaining == 0
            creditsLeft = credits.remaining
            dailyLimit = credits.dailyLimit
            updateComposer()
            if (exhausted) openQuota()
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: CreateIntent) {
        when (intent) {
            is CreateIntent.ComingSoonClicked -> setState { copy(sheet = CreateSheet.ComingSoon(intent.feature)) }

            CreateIntent.StylesClicked -> openComingSoon(ComingSoonFeature.Styles)

            CreateIntent.ToolsClicked -> sendEffect(CreateEffect.OpenTools)

            is CreateIntent.ToolClicked -> sendEffect(CreateEffect.OpenTool(intent.tool))

            is CreateIntent.FilterSelected -> setState { copy(filter = intent.filter) }

            is CreateIntent.IdeaClicked -> updateComposer { copy(prompt = intent.prompt) }

            is CreateIntent.TrackClicked -> play(intent.id)

            is CreateIntent.RetryClicked -> retry(intent.id)

            is CreateIntent.FixClicked -> useFix(intent.id)

            is CreateIntent.DeleteClicked -> askToDelete(intent.id)

            is CreateIntent.MoreClicked -> setState { copy(menuTrackId = intent.id) }

            CreateIntent.MenuDismissed -> setState { copy(menuTrackId = null) }

            is CreateIntent.MenuActionClicked -> onMenuAction(intent.action)

            CreateIntent.DeleteConfirmed -> delete()

            CreateIntent.DeleteDismissed -> setState { copy(deleting = null) }

            is CreateIntent.PromptChanged -> updateComposer { copy(prompt = intent.text) }

            CreateIntent.SendClicked -> send()

            CreateIntent.QuotaClicked -> openQuota()

            is CreateIntent.NotificationAnswered -> answerNotifications(intent.notify)

            is CreateIntent.QualitySelected -> changeSettings { copy(quality = intent.quality) }

            CreateIntent.InstrumentalRemoved -> changeSettings { copy(lyricsMode = LyricsMode.Auto) }

            CreateIntent.LyricsClicked -> setState { copy(sheet = CreateSheet.Lyrics) }

            CreateIntent.CustomLyricsClicked -> {
                changeSettings { copy(lyricsMode = LyricsMode.Custom) }
                setState { copy(sheet = null) }
            }

            is CreateIntent.CustomLyricsChanged -> changeSettings { copy(customLyrics = intent.text.take(5_000)) }

            CreateIntent.ReferenceClicked -> setState { copy(sheet = CreateSheet.Reference) }

            is CreateIntent.ReferenceSelected -> {
                changeSettings { copy(referenceAudioUri = intent.uri) }
                updateComposer { copy(referenceLabel = intent.title) }
                setState { copy(sheet = null) }
            }

            CreateIntent.ReferenceRemoved -> {
                changeSettings { copy(referenceAudioUri = null, remixTrackId = null) }
                updateComposer { copy(referenceLabel = null) }
            }

            is CreateIntent.RecordingSaved -> viewModelScope.launch {
                when (val result = tracks.saveRecording(intent.uri, intent.title, intent.durationMs.milliseconds)) {
                    is Outcome.Success -> setState { copy(sheet = null) }
                    is Outcome.Failure -> showError(result.error)
                }
            }

            is CreateIntent.LyricsSelected -> {
                setState { copy(sheet = null) }
                val mode = if (intent.instrumental) LyricsMode.Instrumental else LyricsMode.Auto
                changeSettings { copy(lyricsMode = mode) }
            }

            is CreateIntent.DurationSelected -> changeSettings { copy(duration = intent.duration) }

            is CreateIntent.RenameChanged -> setState {
                val open = sheet as? CreateSheet.Rename ?: return@setState this
                copy(sheet = open.copy(title = intent.title))
            }

            CreateIntent.RenameConfirmed -> rename()

            CreateIntent.VariantsClicked -> changeSettings {
                copy(variants = if (variants == 1) GenerationSettings.MAX_VARIANTS else 1)
            }

            CreateIntent.SettingsClicked -> openSettings()

            is CreateIntent.SettingsQualitySelected -> editDraft { copy(quality = intent.quality) }

            is CreateIntent.SettingsVariantsSelected -> editDraft { copy(variants = intent.variants) }

            is CreateIntent.SettingsModelSelected -> editDraft { copy(model = intent.model) }

            is CreateIntent.SettingsInstrumentalChanged -> editDraft {
                copy(lyricsMode = if (intent.instrumental) LyricsMode.Instrumental else LyricsMode.Auto)
            }

            CreateIntent.SettingsCustomLyricsSelected -> editDraft { copy(lyricsMode = LyricsMode.Custom) }

            is CreateIntent.SettingsDurationSelected -> editDraft { copy(duration = intent.duration?.toOptionUi()) }

            CreateIntent.SettingsReset -> editDraft { GenerationSettings().toSettingsUi() }

            CreateIntent.SettingsApplied -> applySettings()

            CreateIntent.WantClicked -> want()

            CreateIntent.SheetDismissed -> setState { copy(sheet = null) }
        }
    }

    private fun showTracks(list: List<Track>) {
        val ended = endedGenerations(before = myTracks, after = list)
        myTracks = list
        setState {
            copy(
                tracks = list.map(renames::apply).toGroupedCreateUi(
                    today = java.time.Instant.ofEpochMilli(
                        clock.now().toEpochMilliseconds(),
                    ).atZone(ZoneId.systemDefault()).toLocalDate(),
                    zone = ZoneId.systemDefault(),
                ),
                isLoading = false,
                // A track that went away takes its menu with it.
                menuTrackId = menuTrackId?.takeIf { id -> list.any { it.id == id } },
            )
        }
        ended.forEach { sendEffect(CreateEffect.ShowToast(it.toToast())) }
    }

    /** Plays the ready tracks from the one tapped; tracks still composing or failed can't play. */
    private fun play(id: TrackId) {
        val ready = myTracks.filter { it.status == TrackStatus.Ready }.map(renames::apply)
        val index = ready.indexOfFirst { it.id == id }
        if (index >= 0) {
            playback.play(ready, index)
            sendEffect(CreateEffect.OpenPlayer)
        }
    }

    private fun retry(id: TrackId) {
        viewModelScope.launch {
            val result = tracks.retry(id)
            if (result is Outcome.Failure) showError(result.error)
        }
    }

    private fun useFix(id: TrackId) {
        val fix = state.value.tracks.find { it.row.id == id }?.fix ?: return
        updateComposer { copy(prompt = fix.prompt) }
    }

    private fun onMenuAction(action: TrackMenuAction) {
        val id = state.value.menuTrackId ?: return
        setState { copy(menuTrackId = null) }
        val track = myTracks.find { it.id == id }?.let(renames::apply) ?: return
        when (action) {
            TrackMenuAction.PlayNext -> {
                playNext(track)
                toast(UiText.Resource(R.string.create_toast_play_next), UiText.Raw(track.title))
            }

            TrackMenuAction.Rename -> setState { copy(sheet = CreateSheet.Rename(id, track.title)) }

            TrackMenuAction.UsePrompt -> updateComposer { copy(prompt = track.prompt) }

            TrackMenuAction.Share -> sendEffect(CreateEffect.ShareTrack(track.title, track.prompt, track))

            TrackMenuAction.Download -> sendEffect(CreateEffect.DownloadAudio(track))

            TrackMenuAction.Delete -> askToDelete(id)
        }
    }

    private fun askToDelete(id: TrackId) {
        val row = state.value.tracks.find { it.row.id == id }?.row ?: return
        if (row.status is com.illiamelnyk.studio.core.presentation.track.TrackUi.Status.Failed) {
            deleteTrack(id)
        } else {
            setState { copy(deleting = row) }
        }
    }

    private fun delete() {
        val id = state.value.deleting?.id ?: return
        setState { copy(deleting = null) }
        deleteTrack(id)
    }

    private fun deleteTrack(id: TrackId) {
        viewModelScope.launch {
            val result = tracks.delete(id)
            if (result is Outcome.Success) renames.remove(id)
            if (result is Outcome.Failure) showError(result.error)
        }
    }

    private fun send() {
        val composer = state.value.composer
        if (creditsLeft == 0) {
            openQuota()
            return
        }
        if (!composer.canSend) return
        val submittedSettings = settings
        updateComposer { copy(isSending = true) }
        viewModelScope.launch {
            val result = generateTracks(composer.prompt, submittedSettings)
            // Text typed while the request was on its way stays.
            val clear = result is Outcome.Success && state.value.composer.prompt == composer.prompt
            val clearReference = clear && settings == submittedSettings
            updateComposer {
                copy(
                    prompt = if (clear) "" else prompt,
                    isSending = false,
                    referenceLabel = if (clearReference) null else referenceLabel,
                )
            }
            when (result) {
                is Outcome.Success -> {
                    if (clearReference) changeSettings { copy(referenceAudioUri = null, remixTrackId = null) }
                    setState { copy(filter = TracksFilter.Drafts) }
                    sendEffect(CreateEffect.TracksStarted)
                    if (!notificationChoice.hasAnswered() && state.value.sheet !is CreateSheet.Quota) {
                        setState { copy(sheet = CreateSheet.Notify) }
                    }
                }

                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun rename() {
        val draft = state.value.sheet as? CreateSheet.Rename ?: return
        val title = draft.title.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            when (val result = tracks.rename(draft.id, title)) {
                is Outcome.Success -> {
                    renames.rename(draft.id, title)
                    setState { copy(sheet = null) }
                    showTracks(myTracks)
                }

                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun openSettings() {
        setState { copy(sheet = CreateSheet.Settings(settings.toSettingsUi())) }
    }

    private fun editDraft(edit: SettingsUi.() -> SettingsUi) {
        setState {
            val open = sheet as? CreateSheet.Settings ?: return@setState this
            copy(sheet = open.copy(draft = open.draft.edit()))
        }
    }

    private fun applySettings() {
        val draft = (state.value.sheet as? CreateSheet.Settings)?.draft ?: return
        setState { copy(sheet = null) }
        changeSettings {
            copy(
                model = draft.model,
                quality = draft.quality,
                variants = draft.variants,
                lyricsMode = draft.lyricsMode,
                duration = draft.duration?.duration,
            )
        }
    }

    private fun want() {
        val feature = (state.value.sheet as? CreateSheet.ComingSoon)?.feature ?: return
        setState { copy(sheet = null) }
        toast(
            title = UiText.Resource(R.string.create_toast_noted),
            message = UiText.Resource(R.string.create_toast_noted_text, UiText.Resource(feature.title)),
        )
    }

    private fun openComingSoon(feature: ComingSoonFeature) = setState { copy(sheet = CreateSheet.ComingSoon(feature)) }

    private fun changeSettings(change: GenerationSettings.() -> GenerationSettings) {
        settings = settings.change()
        updateComposer()
    }

    /** Changes the composer with [change], then shows the settings and works out whether it can send. */
    private fun updateComposer(change: ComposerUi.() -> ComposerUi = { this }) {
        setState { copy(composer = composer.change().withSettings(settings).checked(creditsLeft)) }
    }

    private fun openQuota() = setState { copy(sheet = CreateSheet.Quota(dailyLimit)) }

    private fun answerNotifications(notify: Boolean) {
        if (state.value.sheet != CreateSheet.Notify) return
        setState { copy(sheet = null) }
        viewModelScope.launch {
            notificationChoice.answer()
            if (notify) sendEffect(CreateEffect.RequestNotifications)
        }
    }

    private fun showError(error: AppError) {
        if (error == AppError.QuotaExceeded) {
            openQuota()
        } else {
            toast(UiText.Resource(R.string.create_toast_error), error.toUiText(), StudioToastKind.Error)
        }
    }

    private fun toast(title: UiText, message: UiText, kind: StudioToastKind = StudioToastKind.Info) =
        sendEffect(CreateEffect.ShowToast(ToastUi(title, message, kind)))
}

/** "Your track is ready" with its title and length, or why it failed. */
private fun GenerationOutcome.toToast(): ToastUi = when (this) {
    is GenerationOutcome.Ready -> ToastUi(
        title = UiText.Resource(R.string.create_toast_ready),
        message = UiText.Resource(R.string.track_subtitle, track.title, track.duration.toClockText()),
    )

    is GenerationOutcome.Failed -> ToastUi(
        title = UiText.Resource(R.string.create_toast_failed, track.title),
        message = error.toUiText(),
        kind = StudioToastKind.Error,
    )
}
