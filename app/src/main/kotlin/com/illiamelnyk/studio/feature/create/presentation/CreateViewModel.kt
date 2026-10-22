package com.illiamelnyk.studio.feature.create.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.domain.track.GenerationOptions
import com.illiamelnyk.studio.core.domain.track.GenerationOptionsRepository
import com.illiamelnyk.studio.core.domain.track.Quality
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
import com.illiamelnyk.studio.feature.create.domain.endedGenerations
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import java.io.IOException
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateViewModel(
    private val tracks: TrackRepository,
    credits: CreditsRepository,
    private val playback: PlaybackController,
    private val generateTracks: GenerateTracks,
    private val playNext: PlayNext,
    private val clock: Clock,
    private val notificationChoice: NotificationChoice,
    sessions: SessionRepository,
    private val generationOptions: GenerationOptionsRepository,
    private val drafts: ComposerDraftStore = ComposerDraftStore(),
) : MviViewModel<CreateState, CreateIntent, CreateEffect>(
    CreateState(composer = ComposerUi(canGenerate = false, optionsReady = false)),
) {

    /** The tracks as the repository last sent them: to play, to share, and to see generations end. */
    private var myTracks: List<Track> = emptyList()

    /** What the composer generates with; the composer's state shows it. */
    private var settings = GenerationSettings()
    private var options: GenerationOptions? = null
    private var optionsLoading = false
    private var optionsFailed = false

    /** `null` until the credits are known. */
    private var creditsLeft: Int? = null
    private var dailyLimit = 0
    private var isAnsweringNotifications = false
    private var referenceSelection = 0L
    private val renaming = mutableSetOf<TrackId>()
    private val composerOwner = drafts.currentOwner

    init {
        loadOptions()
        sessions.session.map { it?.user?.canGenerate == true }
            .distinctUntilChanged()
            .onEach { allowed -> updateComposer { copy(canGenerate = allowed) } }
            .launchIn(viewModelScope)
        combine(tracks.observeMine(), tracks.observeLibrarySynced(), ::Pair)
            .onEach { (list, synced) -> showTracks(list, synced) }
            .launchIn(viewModelScope)
        drafts.sources.onEach { sources ->
            referenceSelection += 1
            val firstSelection = settings.remixTrackIds.isEmpty() && sources.isNotEmpty()
            changeSettings {
                copy(

                    remixTrackIds = sources.map { it.id.value },
                    referenceAudioUri = if (sources.isNotEmpty()) null else referenceAudioUri,
                )
            }
            updateComposer {
                copy(
                    prompt = if (firstSelection &&
                        sources.first().prompt.isNotBlank()
                    ) {
                        sources.first().prompt
                    } else {
                        prompt
                    },
                    referenceLabel =
                        sources.firstOrNull()?.title ?: referenceLabel.takeIf { settings.referenceAudioUri != null },
                    remixSources = sources.map { RemixSourceUi(it.id, it.title) }.toImmutableList(),
                )
            }
        }.launchIn(viewModelScope)
        drafts.problem.onEach { problem ->
            if (problem != null) {
                val message = when (problem) {
                    ComposerDraftStore.Problem.TooMany -> R.string.create_remix_source_limit
                    ComposerDraftStore.Problem.AlreadyRemixed -> R.string.create_remix_original_only
                    ComposerDraftStore.Problem.NotReady -> R.string.create_remix_ready_only
                    ComposerDraftStore.Problem.RecordingReference -> R.string.create_remix_recording_reference
                }
                toast(UiText.Resource(R.string.create_toast_error), UiText.Resource(message), StudioToastKind.Error)
                drafts.problemShown()
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

            CreateIntent.ComposerNoticeClicked -> if (optionsFailed) {
                loadOptions()
            } else if (creditsLeft ==
                0
            ) {
                openQuota()
            }

            CreateIntent.GenerationOptionsRefresh -> loadOptions()

            is CreateIntent.NotificationAnswered -> answerNotifications(intent.notify)

            is CreateIntent.QualitySelected -> if (intent.quality in options?.qualityModes.orEmpty()) {
                changeSettings { copy(quality = intent.quality) }
            }

            CreateIntent.InstrumentalRemoved -> changeSettings { copy(lyricsMode = LyricsMode.Auto) }

            CreateIntent.LyricsClicked -> setState { copy(sheet = CreateSheet.Lyrics) }

            CreateIntent.CustomLyricsClicked -> if (options?.customLyrics == true) {
                changeSettings { copy(lyricsMode = LyricsMode.Custom) }
                setState { copy(sheet = null) }
            }

            is CreateIntent.CustomLyricsChanged -> changeSettings { copy(customLyrics = intent.text.take(5_000)) }

            CreateIntent.ReferenceClicked -> if (options?.audioReferences == true) {
                setState { copy(sheet = CreateSheet.Reference) }
            }

            is CreateIntent.ReferenceSelected -> if (options?.audioReferences == true) {
                referenceSelection += 1
                drafts.clear()
                changeSettings {
                    copy(referenceAudioUri = intent.uri, remixTrackIds = emptyList())
                }
                updateComposer {
                    copy(referenceLabel = intent.title, remixSources = kotlinx.collections.immutable.persistentListOf())
                }
                setState { copy(sheet = null) }
            }

            CreateIntent.ReferenceRemoved -> {
                referenceSelection += 1
                drafts.clear()
                changeSettings { copy(referenceAudioUri = null, remixTrackIds = emptyList()) }
                updateComposer {
                    copy(referenceLabel = null, remixSources = kotlinx.collections.immutable.persistentListOf())
                }
            }

            is CreateIntent.RemixSourceRemoved -> drafts.remove(intent.id)

            is CreateIntent.RemixSourceClicked -> drafts.sources.value.find { it.id == intent.id }?.let {
                playback.play(listOf(it))
                sendEffect(CreateEffect.OpenPlayer)
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

            CreateIntent.VariantsClicked -> options?.variantCounts?.sorted()?.let { counts ->
                changeSettings { copy(variants = counts[(counts.indexOf(variants) + 1) % counts.size]) }
            }

            CreateIntent.SettingsClicked -> openSettings()

            is CreateIntent.SettingsQualitySelected -> if (intent.quality in options?.qualityModes.orEmpty()) {
                editDraft { copy(quality = intent.quality) }
            }

            is CreateIntent.SettingsVariantsSelected -> if (intent.variants in options?.variantCounts.orEmpty()) {
                editDraft { copy(variants = intent.variants) }
            }

            is CreateIntent.SettingsModelSelected -> editDraft { copy(model = intent.model) }

            is CreateIntent.SettingsInstrumentalChanged -> editDraft {
                copy(lyricsMode = if (intent.instrumental) LyricsMode.Instrumental else LyricsMode.Auto)
            }

            CreateIntent.SettingsCustomLyricsSelected -> if (options?.customLyrics == true) {
                editDraft { copy(lyricsMode = LyricsMode.Custom) }
            }

            is CreateIntent.SettingsDurationSelected -> editDraft { copy(duration = intent.duration?.toOptionUi()) }

            CreateIntent.SettingsReset -> editDraft {
                GenerationSettings().normalized(options).toSettingsUi().withOptions(options)
            }

            CreateIntent.SettingsApplied -> applySettings()

            CreateIntent.WantClicked -> want()

            CreateIntent.SheetDismissed -> setState { copy(sheet = null) }
        }
    }

    /** @param synced whether the library came from the server: until then, no tracks means loading. */
    private fun showTracks(list: List<Track>, synced: Boolean) {
        val ended = endedGenerations(before = myTracks, after = list)
        myTracks = list
        setState {
            copy(
                tracks = list.toGroupedCreateUi(
                    today = java.time.Instant.ofEpochMilli(
                        clock.now().toEpochMilliseconds(),
                    ).atZone(ZoneId.systemDefault()).toLocalDate(),
                    zone = ZoneId.systemDefault(),
                ),
                isLoading = list.isEmpty() && !synced,
                // A track that went away takes its menu with it.
                menuTrackId = menuTrackId?.takeIf { id -> list.any { it.id == id } },
            )
        }
        ended.forEach { sendEffect(CreateEffect.ShowToast(it.toToast())) }
    }

    /** Plays the ready tracks from the one tapped; tracks still composing or failed can't play. */
    private fun play(id: TrackId) {
        val ready = myTracks.filter { it.status == TrackStatus.Ready }
        val index = ready.indexOfFirst { it.id == id }
        if (index >= 0) {
            if (playback.state.value.current?.id != id) playback.play(ready, index)
            sendEffect(CreateEffect.OpenPlayer)
        }
    }

    private fun retry(id: TrackId) {
        if (!state.value.composer.canGenerate) {
            showError(AppError.GenerationNotAllowed)
            return
        }
        viewModelScope.launch {
            if (!state.value.composer.canGenerate) return@launch
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
        val track = myTracks.find { it.id == id } ?: return
        when (action) {
            TrackMenuAction.PlayNext -> {
                if (playback.state.value.current?.id == track.id) return
                playNext(track)
                toast(UiText.Resource(R.string.create_toast_play_next), UiText.Raw(track.title))
            }

            TrackMenuAction.Rename -> setState { copy(sheet = CreateSheet.Rename(id, track.title)) }

            TrackMenuAction.UsePrompt -> if (track.prompt.isNotBlank()) {
                updateComposer { copy(prompt = track.prompt) }
            }

            TrackMenuAction.UseAsReference -> useRecordingReference(track)

            TrackMenuAction.Share -> sendEffect(CreateEffect.ShareTrack(track.title, track.prompt, track))

            TrackMenuAction.Download -> sendEffect(CreateEffect.DownloadAudio(track))

            TrackMenuAction.Delete -> askToDelete(id)
        }
    }

    private fun useRecordingReference(track: Track) {
        if (!track.canUseAsReference || composerOwner == null || drafts.currentOwner != composerOwner) return
        if (options?.audioReferences != true) {
            showError(AppError.UnsupportedMode)
            return
        }
        val selection = ++referenceSelection
        viewModelScope.launch {
            when (val result = tracks.prepareRecordingReference(track.id)) {
                is Outcome.Success -> {
                    if (selection != referenceSelection || drafts.currentOwner != composerOwner ||
                        result.value.ownerId != composerOwner || !tracks.isRecordingReferenceCurrent(result.value)
                    ) {
                        return@launch
                    }
                    onIntent(CreateIntent.ReferenceSelected(result.value.uri, result.value.title))
                }

                is Outcome.Failure -> if (selection == referenceSelection) {
                    toast(
                        UiText.Resource(R.string.create_toast_error),
                        result.error.toUiText(),
                        StudioToastKind.Error,
                    )
                }
            }
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
            val result = withContext(NonCancellable) {
                tracks.delete(id).also { outcome ->
                    if (outcome is Outcome.Success) {
                        playback.removeTrack(id)
                    }
                }
            }
            if (result is Outcome.Failure) showError(result.error)
        }
    }

    private fun send() {
        val composer = state.value.composer
        if (!composer.canGenerate) {
            showError(AppError.GenerationNotAllowed)
            return
        }
        if (creditsLeft == 0) {
            openQuota()
            return
        }
        if (!composer.canSend) return
        val submittedSettings = settings
        updateComposer { copy(isSending = true) }
        viewModelScope.launch {
            if (!state.value.composer.canGenerate) {
                updateComposer { copy(isSending = false) }
                return@launch
            }
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
                    if (clearReference) {
                        drafts.clear()
                        changeSettings {
                            copy(referenceAudioUri = null, remixTrackIds = emptyList())
                        }
                    }
                    setState { copy(filter = TracksFilter.Drafts) }
                    sendEffect(CreateEffect.TracksStarted)
                    try {
                        if (!notificationChoice.hasAnswered() && state.value.sheet !is CreateSheet.Quota) {
                            setState { copy(sheet = CreateSheet.Notify) }
                        }
                    } catch (_: IOException) {
                        showPreferenceError()
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
        if (!renaming.add(draft.id)) return
        viewModelScope.launch {
            try {
                when (val result = tracks.rename(draft.id, title)) {
                    is Outcome.Success -> {
                        setState { if (sheet === draft) copy(sheet = null) else this }
                    }

                    is Outcome.Failure -> showError(result.error)
                }
            } finally {
                renaming.remove(draft.id)
            }
        }
    }

    private fun openSettings() {
        setState { copy(sheet = CreateSheet.Settings(settings.toSettingsUi().withOptions(options))) }
    }

    private fun editDraft(edit: SettingsUi.() -> SettingsUi) {
        setState {
            val open = sheet as? CreateSheet.Settings ?: return@setState this
            copy(sheet = open.copy(draft = open.draft.edit()))
        }
    }

    private fun applySettings() {
        val draft = (state.value.sheet as? CreateSheet.Settings)?.draft ?: return
        if (!draft.optionsReady || (draft.lyricsMode == LyricsMode.Custom && !draft.customLyricsSupported)) return
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
        settings = settings.change().normalized(options)
        updateComposer()
    }

    /** Changes the composer with [change], then shows the settings and works out whether it can send. */
    private fun updateComposer(change: ComposerUi.() -> ComposerUi = { this }) {
        val current = options
        val optionsNotice = when {
            optionsFailed -> R.string.create_options_failed

            current == null -> R.string.create_options_loading

            settings.lyricsMode == LyricsMode.Custom && !current.customLyrics ->
                R.string.create_options_lyrics_unsupported

            settings.referenceAudioUri != null && !current.audioReferences ->
                R.string.create_options_reference_unsupported

            settings.remixTrackIds.isNotEmpty() && !current.audioReferences ->
                R.string.create_options_remix_unsupported

            settings.remixTrackIds.size > current.maxRemixSources -> R.string.create_options_remix_unsupported

            else -> null
        }
        setState {
            copy(
                composer = composer.change().withSettings(settings).copy(
                    qualityOptions = Quality.entries.filter { it in current?.qualityModes.orEmpty() }.toImmutableList(),
                    variantOptions = current?.variantCounts.orEmpty().sorted().toImmutableList(),
                    customLyricsSupported = current?.customLyrics == true,
                    audioReferencesSupported = current?.audioReferences == true,
                    optionsReady = current != null,
                    optionsNotice = optionsNotice?.let { UiText.Resource(it) },
                    connecting = current == null && !optionsFailed,
                ).checked(creditsLeft),
            )
        }
    }

    private fun loadOptions() {
        if (optionsLoading) return
        optionsLoading = true
        optionsFailed = false
        options = null
        updateComposer()
        viewModelScope.launch {
            try {
                when (val result = generationOptions.load()) {
                    is Outcome.Success -> {
                        options = result.value
                        settings = settings.normalized(options)
                    }

                    is Outcome.Failure -> optionsFailed = true
                }
                editDraft {
                    copy(
                        quality = settingsForOptions(quality, options),
                        variants = variantsForOptions(variants, options),
                    ).withOptions(options)
                }
                updateComposer()
            } finally {
                optionsLoading = false
            }
        }
    }

    private fun openQuota() = setState { copy(sheet = CreateSheet.Quota(dailyLimit)) }

    private fun answerNotifications(notify: Boolean) {
        if (state.value.sheet != CreateSheet.Notify || isAnsweringNotifications) return
        isAnsweringNotifications = true
        viewModelScope.launch {
            try {
                notificationChoice.answer(notify)
                setState { if (sheet == CreateSheet.Notify) copy(sheet = null) else this }
                if (notify) sendEffect(CreateEffect.RequestNotifications)
            } catch (_: IOException) {
                showPreferenceError()
            } finally {
                isAnsweringNotifications = false
            }
        }
    }

    private fun showPreferenceError() = toast(
        UiText.Resource(R.string.create_toast_error),
        UiText.Resource(R.string.error_preferences),
        StudioToastKind.Error,
    )

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
