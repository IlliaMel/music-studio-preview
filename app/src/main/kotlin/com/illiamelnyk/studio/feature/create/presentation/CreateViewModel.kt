package com.illiamelnyk.studio.feature.create.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.Style
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
import com.illiamelnyk.studio.feature.create.domain.PlayNext
import com.illiamelnyk.studio.feature.create.domain.StyleCatalog
import com.illiamelnyk.studio.feature.create.domain.endedGenerations
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonFeature
import com.illiamelnyk.studio.feature.create.presentation.common.ToastUi
import com.illiamelnyk.studio.feature.create.presentation.common.toOptionUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CreateViewModel(
    private val tracks: TrackRepository,
    credits: CreditsRepository,
    private val styleCatalog: StyleCatalog,
    private val playback: PlaybackController,
    private val generateTracks: GenerateTracks,
    private val playNext: PlayNext,
) : MviViewModel<CreateState, CreateIntent, CreateEffect>(CreateState()) {

    /** The tracks as the repository last sent them: to play, to share, and to see generations end. */
    private var myTracks: List<Track> = emptyList()
    private var styles: List<Style> = emptyList()
    private var stylesLoading: Job? = null

    /** What the composer generates with; the composer's state shows it. */
    private var settings = GenerationSettings()

    /** `null` until the credits are known. */
    private var creditsLeft: Int? = null

    init {
        tracks.observeMine().onEach(::showTracks).launchIn(viewModelScope)
        playback.state.map { it.current?.id }
            .distinctUntilChanged()
            .onEach { id -> setState { copy(playingTrackId = id) } }
            .launchIn(viewModelScope)
        credits.credits.onEach { credits ->
            creditsLeft = credits.remaining
            updateComposer()
        }.launchIn(viewModelScope)
        loadStyles()
    }

    override fun onIntent(intent: CreateIntent) = when (intent) {
        is CreateIntent.ComingSoonClicked -> setState { copy(sheet = CreateSheet.ComingSoon(intent.feature)) }

        CreateIntent.StylesClicked -> openStyles()

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

        is CreateIntent.QualitySelected -> changeSettings { copy(quality = intent.quality) }

        CreateIntent.StyleRemoved -> changeSettings { copy(style = null) }

        CreateIntent.VariantsClicked -> changeSettings {
            copy(variants = if (variants == 1) GenerationSettings.MAX_VARIANTS else 1)
        }

        CreateIntent.SettingsClicked -> openSettings()

        is CreateIntent.StyleSelected -> selectStyle(intent.id)

        is CreateIntent.SettingsModelSelected -> editDraft { copy(model = intent.model) }

        is CreateIntent.SettingsInstrumentalChanged -> editDraft { copy(instrumental = intent.instrumental) }

        is CreateIntent.SettingsDurationSelected -> editDraft { copy(duration = intent.duration.toOptionUi()) }

        is CreateIntent.SettingsStyleSelected -> editDraft { copy(styleId = intent.id) }

        CreateIntent.SettingsReset -> editDraft { GenerationSettings().toSettingsUi() }

        CreateIntent.SettingsApplied -> applySettings()

        CreateIntent.WantClicked -> want()

        CreateIntent.SheetDismissed -> setState { copy(sheet = null) }
    }

    private fun showTracks(list: List<Track>) {
        val ended = endedGenerations(before = myTracks, after = list)
        myTracks = list
        setState {
            copy(
                tracks = list.map(Track::toCreateUi).toImmutableList(),
                isLoading = false,
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
        if (index >= 0) playback.play(ready, index)
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
        val track = myTracks.find { it.id == id } ?: return
        when (action) {
            TrackMenuAction.PlayNext -> {
                playNext(track)
                toast(UiText.Resource(R.string.create_toast_play_next), UiText.Raw(track.title))
            }

            TrackMenuAction.Like -> viewModelScope.launch {
                val result = tracks.setLiked(id, liked = !track.isLiked)
                if (result is Outcome.Failure) showError(result.error)
            }

            TrackMenuAction.AddToPlaylist -> openComingSoon(ComingSoonFeature.AddToPlaylist)

            TrackMenuAction.Share -> sendEffect(CreateEffect.ShareTrack(track.title, track.prompt))

            TrackMenuAction.Download -> openComingSoon(ComingSoonFeature.Download)

            TrackMenuAction.Delete -> askToDelete(id)
        }
    }

    private fun askToDelete(id: TrackId) {
        val row = state.value.tracks.find { it.row.id == id }?.row ?: return
        setState { copy(deleting = row) }
    }

    private fun delete() {
        val id = state.value.deleting?.id ?: return
        setState { copy(deleting = null) }
        viewModelScope.launch {
            val result = tracks.delete(id)
            if (result is Outcome.Failure) showError(result.error)
        }
    }

    private fun send() {
        val composer = state.value.composer
        if (!composer.canSend) return
        updateComposer { copy(isSending = true) }
        viewModelScope.launch {
            val result = generateTracks(composer.prompt, settings)
            // Text typed while the request was on its way stays.
            val clear = result is Outcome.Success && state.value.composer.prompt == composer.prompt
            updateComposer { copy(prompt = if (clear) "" else prompt, isSending = false) }
            when (result) {
                is Outcome.Success -> {
                    setState { copy(filter = TracksFilter.Drafts) }
                    sendEffect(CreateEffect.TracksStarted)
                }

                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun openStyles() {
        setState { copy(sheet = CreateSheet.Styles) }
        if (state.value.styles == null) loadStyles()
    }

    private fun selectStyle(id: String) {
        val style = styles.find { it.id == id } ?: return
        setState { copy(sheet = null) }
        changeSettings { copy(style = style) }
    }

    private fun openSettings() {
        setState { copy(sheet = CreateSheet.Settings(settings.toSettingsUi())) }
        if (state.value.styles == null) loadStyles()
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
                instrumental = draft.instrumental,
                duration = draft.duration.duration,
                style = styles.find { it.id == draft.styleId },
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

    private fun loadStyles() {
        if (stylesLoading?.isActive == true) return
        stylesLoading = viewModelScope.launch {
            when (val result = styleCatalog.styles()) {
                is Outcome.Success -> {
                    styles = result.value
                    setState { copy(styles = result.value.map(Style::toUi).toImmutableList()) }
                }

                // The sheets keep their placeholders; opening one asks again.
                is Outcome.Failure -> showError(result.error)
            }
        }
    }

    private fun changeSettings(change: GenerationSettings.() -> GenerationSettings) {
        settings = settings.change()
        updateComposer()
    }

    /** Changes the composer with [change], then shows the settings and works out whether it can send. */
    private fun updateComposer(change: ComposerUi.() -> ComposerUi = { this }) {
        setState { copy(composer = composer.change().withSettings(settings).checked(creditsLeft)) }
    }

    private fun showError(error: AppError) =
        toast(UiText.Resource(R.string.create_toast_error), error.toUiText(), StudioToastKind.Error)

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
