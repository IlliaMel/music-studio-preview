package com.illiamelnyk.studio.feature.player.presentation

import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.LyricsRepository
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.presentation.MviViewModel
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.toUiText
import com.illiamelnyk.studio.core.ui.components.StudioToastKind
import kotlin.math.roundToLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The full player: follows the one [PlaybackController], loads the lyrics of each track and keeps
 * the like of the current one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val playback: PlaybackController,
    private val lyrics: LyricsRepository,
    private val tracks: TrackRepository,
    private val drafts: ComposerDraftStore = ComposerDraftStore(),
) : MviViewModel<PlayerState, PlayerIntent, PlayerEffect>(
    // Built from what is playing right now, so the player never opens on an empty first frame.
    PlayerState(PlayerContent().withPlayback(playback.state.value), playback.state.value.toClock()),
) {

    /**
     * Likes set here, by track: shown at once, ahead of the repository, and kept for tracks it
     * doesn't store, such as the catalog's. An entry goes once the repository has caught up.
     */
    private val likedHere = MutableStateFlow<Map<TrackId, Boolean>>(emptyMap())
    private var likeSequence = 0L
    private val likeRequests = mutableMapOf<TrackId, Long>()

    init {
        // Everything but the position: the track, the controls, the queue.
        playback.state
            .map { it.copy(positionMs = 0) }
            .distinctUntilChanged()
            .onEach { playbackState -> setState { copy(content = content.withPlayback(playbackState)) } }
            .launchIn(viewModelScope)
        playback.state
            .map { it.toClock() }
            .distinctUntilChanged()
            .onEach { clock -> setState { copy(clock = clock) } }
            .launchIn(viewModelScope)

        val currentTrack = playback.state.map { it.current }.distinctUntilChanged { old, new -> old?.id == new?.id }
        // A new track cancels the lyrics request of the previous one.
        viewModelScope.launch {
            currentTrack.flatMapLatest { playing ->
                if (playing == null) flowOf(null) else tracks.observeById(playing.id).map { it ?: playing }
            }.distinctUntilChanged { old, new ->
                old?.id == new?.id && old?.words == new?.words &&
                    old?.customLyrics == new?.customLyrics && old?.instrumental == new?.instrumental
            }.collectLatest(::loadLyrics)
        }
        currentTrack
            .flatMapLatest(::likedFlow)
            .distinctUntilChanged()
            .onEach { liked -> setContent { copy(isLiked = liked) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.CollapseClicked -> sendEffect(PlayerEffect.Collapse)

            PlayerIntent.PlayPauseClicked -> playback.togglePlayPause()

            PlayerIntent.StopClicked -> playback.stop()

            PlayerIntent.NextClicked -> playback.next()

            PlayerIntent.PreviousClicked -> playback.previous()

            is PlayerIntent.ShuffleChanged -> playback.setShuffle(intent.enabled)

            is PlayerIntent.RepeatChanged -> playback.setRepeat(if (intent.enabled) RepeatMode.All else RepeatMode.Off)

            is PlayerIntent.AutoplayChanged -> playback.setAutoplay(intent.enabled)

            is PlayerIntent.SeekRequested -> seekTo(intent.fraction)

            is PlayerIntent.LyricLineClicked -> playback.seekTo(intent.startMs)

            PlayerIntent.LyricsClicked -> setContent {
                copy(showLyrics = if (sheet == PlayerSheet.Queue) true else !showLyrics, sheet = null)
            }

            PlayerIntent.QueueClicked -> setContent {
                val next = if (sheet == PlayerSheet.Queue) null else PlayerSheet.Queue
                copy(sheet = next)
            }

            is PlayerIntent.UpNextTrackClicked -> playUpNext(intent.position)

            is PlayerIntent.QueueItemMoved -> {
                val playing = playback.state.value
                if (!playing.isStation && intent.from >= 0 && intent.to >= 0) {
                    playback.moveQueueItem(playing.currentIndex + 1 + intent.from, playing.currentIndex + 1 + intent.to)
                }
            }

            PlayerIntent.ClearUpNextClicked -> playback.clearUpNext()

            PlayerIntent.SheetDismissed -> setContent { copy(sheet = null) }

            PlayerIntent.AddToClicked -> openPlaylistPicker()

            PlayerIntent.LikeClicked -> toggleLike()

            PlayerIntent.ShareClicked -> share()

            is PlayerIntent.ComingSoonClicked -> when (intent.feature) {
                SoonFeature.Download -> playback.state.value.current?.let { sendEffect(PlayerEffect.Download(it)) }
                SoonFeature.Remix -> remixCurrentTrack()
                SoonFeature.Autoplay -> playback.setAutoplay(!playback.state.value.autoplay)
            }

            PlayerIntent.WantItClicked -> wantIt()
        }
    }

    private fun remixCurrentTrack() {
        val current = playback.state.value
        if (current.isStation) return
        val track = current.current?.takeIf { it.canRemixOriginal } ?: return
        drafts.remix(track)
        sendEffect(PlayerEffect.Collapse)
    }

    private fun setContent(reduce: PlayerContent.() -> PlayerContent) = setState { copy(content = content.reduce()) }

    private suspend fun loadLyrics(track: Track?) {
        if (track == null) return
        // An instrumental has nothing to sing along to, so nothing to ask for.
        if (track.instrumental) {
            setContent { copy(lyrics = LyricsUi.None) }
            return
        }
        setContent { copy(lyrics = LyricsUi.Loading) }
        val loaded = lyrics.lyrics(track.id).toLyricsUi()
        setContent { copy(lyrics = loaded) }
    }

    /** Whether [track] is liked: as set here, else as the repository keeps it, else as it came. */
    private fun likedFlow(track: Track?): Flow<Boolean> {
        if (track == null) return flowOf(false)
        return combine(tracks.observeById(track.id), likedHere) { saved, here ->
            here[track.id] ?: saved?.isLiked ?: track.isLiked
        }
    }

    private fun openPlaylistPicker() {
        val track = playback.state.value.current ?: return
        sendEffect(PlayerEffect.OpenPlaylistPicker(track))
    }

    private fun toggleLike() {
        val content = state.value.content
        val id = content.track?.id ?: return
        val liked = !(likedHere.value[id] ?: content.isLiked)
        val request = ++likeSequence
        likeRequests[id] = request
        likedHere.update { it + (id to liked) }
        viewModelScope.launch {
            val outcome = tracks.setLiked(id, liked)
            // A failure undoes the tap; a success hands over to the repository when it keeps the
            // track. Either way only if no later tap has set the like since.
            val settled = outcome is Outcome.Failure || tracks.observeById(id).first() != null
            // Boolean equality cannot distinguish first and third taps in true?false?true.
            if (likeRequests[id] != request) return@launch
            if (settled) likedHere.update { it - id }
            likeRequests.remove(id)
            if (outcome is Outcome.Failure) {
                val toast = PlayerToastUi(
                    title = UiText.Resource(R.string.player_like_failed),
                    message = outcome.error.toUiText(),
                    kind = StudioToastKind.Error,
                )
                sendEffect(PlayerEffect.ShowToast(toast))
            }
        }
    }

    private fun seekTo(fraction: Float) {
        val clock = state.value.clock
        if (state.value.content.isStation || clock.durationMs <= 0) return
        playback.seekTo((fraction.coerceIn(0f, 1f) * clock.durationMs).roundToLong())
    }

    /** Plays on from the [position]th track after the current one. A station has no queue to pick from. */
    private fun playUpNext(position: Int) {
        val playing = playback.state.value
        val index = playing.currentIndex + 1 + position
        if (playing.isStation || position < 0 || index !in playing.queue.indices) return
        playback.play(playing.queue, index)
    }

    private fun share() {
        val track = playback.state.value.current ?: return
        sendEffect(PlayerEffect.ShareAudio(track))
    }

    private fun wantIt() {
        setContent { copy(sheet = null) }
        val toast = PlayerToastUi(
            title = UiText.Resource(R.string.player_want_it_title),
            message = UiText.Resource(R.string.player_want_it_message),
        )
        sendEffect(PlayerEffect.ShowToast(toast))
    }
}
