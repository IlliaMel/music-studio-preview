package com.illiamelnyk.studio.core.data.playback

import androidx.core.content.edit
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.data.local.toTrack
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Track
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidPlaybackController(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) : PlaybackController {
    private val playbackLock = Any()
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private var originalQueue: List<Track> = emptyList()

    private var playOrder: List<Int> = emptyList()
    private var clock: Job? = null
    private var pendingSeek: Long? = null
    private val audioManager = context.getSystemService(android.media.AudioManager::class.java)
    private var hasFocus = false
    private val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
        )
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                android.media.AudioManager.AUDIOFOCUS_LOSS, android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasFocus = false
                    mutableState.update { it.copy(isPlaying = false) }
                }

                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f, 0.2f)

                android.media.AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f)
            }
        }.build()
    private val preferences = context.getSharedPreferences("playback", android.content.Context.MODE_PRIVATE)

    init {
        runCatching {
            val saved = preferences.getString("state", null)?.let { org.json.JSONObject(it) } ?: return@runCatching
            val array = saved.getJSONArray("queue")
            originalQueue = (0 until array.length()).map { array.getJSONObject(it).toTrack() }
            playOrder = originalQueue.indices.toList()
            mutableState.value = PlaybackState(
                queue = originalQueue,
                currentIndex = saved.optInt("index").coerceIn(0, originalQueue.lastIndex.coerceAtLeast(0)),
                positionMs = saved.optLong("position"),
                shuffle = saved.optBoolean("shuffle"),
                repeat = RepeatMode.entries.firstOrNull { it.name == saved.optString("repeat") } ?: RepeatMode.Off,
                stationName = saved.optString("station").takeIf { it.isNotBlank() && it != "null" },
            )
        }.onFailure { android.util.Log.w("StudioPlayback", "Cannot restore queue", it) }
        scope.launch {
            mutableState.map { it.copy(positionMs = it.positionMs / 1000 * 1000) }
                .distinctUntilChanged().collect { state ->
                    val saved = org.json.JSONObject().apply {
                        put("queue", org.json.JSONArray(state.queue.map { it.toJson() }))
                        put("index", state.currentIndex)
                        put("position", state.positionMs)
                        put("shuffle", state.shuffle)
                        put("repeat", state.repeat.name)
                        put("station", state.stationName)
                    }
                    preferences.edit { putString("state", saved.toString()) }
                }
        }
    }

    override fun play(queue: List<Track>, startIndex: Int) = synchronized(playbackLock) {
        load(queue, startIndex, stationName = null)
    }

    override fun playStation(name: String, queue: List<Track>) = synchronized(playbackLock) {
        load(queue, startIndex = 0, stationName = name)
    }

    override fun enqueueNext(track: Track) {
        synchronized(playbackLock) {
            val state = mutableState.value
            val current = state.current
            if (current == null) {
                play(listOf(track))
                return
            }
            if (current.id == track.id) return
            val currentOriginal = playOrder[state.currentIndex]
            val retained = originalQueue.indices.filter { originalQueue[it].id != track.id }
            val insertAt = retained.indexOf(currentOriginal) + 1
            val updated = retained.map(originalQueue::get).toMutableList().apply { add(insertAt, track) }
            val remapped = retained.mapIndexed { index, old ->
                old to if (index >=
                    insertAt
                ) {
                    index + 1
                } else {
                    index
                }
            }.toMap()
            val order = playOrder.mapNotNull(remapped::get).toMutableList()
            val currentIndex = order.indexOf(remapped.getValue(currentOriginal))
            order.add(currentIndex + 1, insertAt)
            originalQueue = updated
            playOrder = order
            mutableState.update { it.copy(queue = order.map(updated::get), currentIndex = currentIndex) }
        }
    }

    override fun togglePlayPause() {
        synchronized(playbackLock) {
            val state = mutableState.value
            if (state.current == null) return
            if (state.isPlaying) {
                mutableState.update { it.copy(isPlaying = false) }
            } else {
                if (!prepared) loadedId = null
                val atEnd = state.positionMs >= state.durationMs
                if (atEnd) pendingSeek = 0
                mutableState.update { it.copy(isPlaying = true, positionMs = if (atEnd) 0 else it.positionMs) }
                startClock()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        synchronized(playbackLock) {
            pendingSeek = positionMs.coerceIn(0, mutableState.value.durationMs)
            mutableState.update { it.copy(positionMs = pendingSeek!!) }
        }
    }

    override fun next() {
        synchronized(playbackLock) {
            if (!mutableState.value.hasNext) return
            pendingSeek = 0
            mutableState.update { state ->
                when {
                    state.currentIndex < state.queue.lastIndex -> state.atTrack(state.currentIndex + 1)
                    state.loops && state.queue.isNotEmpty() -> state.atTrack(0)
                    else -> state
                }
            }
        }
    }

    override fun previous() {
        synchronized(playbackLock) {
            pendingSeek = 0
            mutableState.update { state ->
                when {
                    state.positionMs > RESTART_THRESHOLD.inWholeMilliseconds -> state.copy(positionMs = 0)
                    state.currentIndex > 0 -> state.atTrack(state.currentIndex - 1)
                    state.loops && state.queue.isNotEmpty() -> state.atTrack(state.queue.lastIndex)
                    else -> state.copy(positionMs = 0)
                }
            }
        }
    }

    override fun setShuffle(enabled: Boolean) {
        synchronized(playbackLock) {
            val state = mutableState.value
            if (state.shuffle == enabled) return
            if (state.current == null) {
                mutableState.update { it.copy(shuffle = enabled) }
                return
            }
            val originalIndex = playOrder[state.currentIndex]
            playOrder = if (enabled) shuffledOrder(first = originalIndex) else originalQueue.indices.toList()
            mutableState.update {
                it.copy(
                    queue = playOrder.map(originalQueue::get),
                    currentIndex = if (enabled) 0 else originalIndex,
                    shuffle = enabled,
                )
            }
        }
    }

    override fun setRepeat(mode: RepeatMode) {
        synchronized(playbackLock) {
            mutableState.update { it.copy(repeat = mode) }
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        synchronized(playbackLock) {
            val state = mutableState.value
            if (fromIndex !in state.queue.indices || toIndex !in state.queue.indices || fromIndex == toIndex) return
            val currentOriginal = playOrder.getOrNull(state.currentIndex) ?: return
            val order = playOrder.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            val currentIndex = order.indexOf(currentOriginal)
            if (state.shuffle) {
                playOrder = order
            } else {
                originalQueue = order.map(originalQueue::get)
                playOrder = originalQueue.indices.toList()
            }
            mutableState.update {
                it.copy(queue = playOrder.map(originalQueue::get), currentIndex = currentIndex)
            }
        }
    }

    override fun clearUpNext() {
        synchronized(playbackLock) {
            val current = mutableState.value.current ?: return
            originalQueue = listOf(current)
            playOrder = listOf(0)
            mutableState.update { it.copy(queue = originalQueue, currentIndex = 0) }
        }
    }

    override fun stop() {
        synchronized(playbackLock) {
            preparing?.cancel()
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
            player?.release()
            player = null
            loadedId = null
            prepared = false
            pendingSeek = null
            stopClock()
            context.stopService(android.content.Intent(context, PlaybackService::class.java))
            originalQueue = emptyList()
            playOrder = emptyList()
            mutableState.update { PlaybackState(shuffle = it.shuffle, repeat = it.repeat) }
        }
    }

    private fun load(queue: List<Track>, startIndex: Int, stationName: String?) {
        require(startIndex in queue.indices) { "Start index $startIndex is outside the queue of ${queue.size}" }
        pendingSeek = 0
        originalQueue = queue
        val shuffle = mutableState.value.shuffle
        playOrder = if (shuffle) shuffledOrder(first = startIndex) else queue.indices.toList()
        mutableState.update {
            it.copy(
                queue = playOrder.map(queue::get),
                currentIndex = if (shuffle) 0 else startIndex,
                positionMs = 0,
                isPlaying = true,
                stationName = stationName,
            )
        }
        startClock()
    }

    private fun startClock() {
        if (clock?.isActive == true) return
        context.startForegroundService(android.content.Intent(context, PlaybackService::class.java))
        clock = scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            while (isActive) {
                syncAudio()
                delay(TICK)
                val active = player
                if (prepared && active != null && active.isPlaying && pendingSeek == null &&
                    loadedId == mutableState.value.current?.id?.value
                ) {
                    mutableState.update { it.copy(positionMs = active.currentPosition.toLong()) }
                }
            }
        }
    }

    private var player: android.media.MediaPlayer? = null
    private var prepared = false
    private var loadedId: String? = null
    private var preparing: Job? = null

    private fun syncAudio() {
        val state = mutableState.value
        val track = state.current
        if (track == null) {
            player?.release()
            player = null
            loadedId = null
            prepared = false
            return
        }
        if (loadedId != track.id.value) {
            preparing?.cancel()
            player?.release()
            player = null
            loadedId = track.id.value
            prepared = false
            preparing = scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                try {
                    val uri = AudioFiles(context).playableUri(track)
                    if (loadedId != track.id.value) return@launch
                    val next = android.media.MediaPlayer()
                    player = next
                    next.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                    next.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    )
                    next.setDataSource(context, uri, track.audioHeaders)
                    next.setOnPreparedListener {
                        if (player !== next) return@setOnPreparedListener
                        prepared = true
                        val actualDuration = next.duration.toLong().milliseconds
                        originalQueue = originalQueue.map {
                            if (it.id == track.id) it.copy(duration = actualDuration) else it
                        }
                        mutableState.update { current ->
                            current.copy(
                                queue = current.queue.map {
                                    if (it.id == track.id) it.copy(duration = actualDuration) else it
                                },
                            )
                        }
                        next.seekTo(mutableState.value.positionMs.toInt())
                        pendingSeek = null
                        if (mutableState.value.isPlaying && requestFocus()) next.start()
                    }
                    next.setOnCompletionListener {
                        val before = mutableState.value
                        val after = before.copy(positionMs = before.durationMs).advancedBy(1)
                        mutableState.value = after
                        if (after.current?.id == before.current?.id && after.isPlaying) {
                            next.seekTo(0)
                            next.start()
                        }
                    }
                    next.setOnErrorListener { _, what, extra ->
                        android.util.Log.e("StudioPlayback", "Audio error: $what/$extra")
                        prepared = false
                        mutableState.update { it.copy(isPlaying = false) }
                        true
                    }
                    next.prepareAsync()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    android.util.Log.e("StudioPlayback", "Cannot open audio", error)
                    mutableState.update { it.copy(isPlaying = false) }
                }
            }
            return
        }
        val active = player ?: return
        if (!prepared) return
        if (state.isPlaying && !active.isPlaying && requestFocus()) active.start()
        if (!state.isPlaying && active.isPlaying) {
            active.pause()
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
        val seek = pendingSeek
        if (seek != null || kotlin.math.abs(active.currentPosition.toLong() - state.positionMs) > 600) {
            active.seekTo((seek ?: state.positionMs).toInt())
            pendingSeek = null
        }
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) mutableState.update { it.copy(isPlaying = false) }
        return hasFocus
    }

    private fun stopClock() {
        clock?.cancel()
        clock = null
    }

    private fun shuffledOrder(first: Int): List<Int> =
        listOf(first) + originalQueue.indices.filter { it != first }.shuffled(random)

    companion object {
        val TICK = 250.milliseconds

        val RESTART_THRESHOLD = 3.seconds
    }
}

private fun PlaybackState.atTrack(index: Int) = copy(currentIndex = index, positionMs = 0)

private fun PlaybackState.advancedBy(elapsedMs: Long): PlaybackState {
    if (!isPlaying || current == null) return this
    val position = positionMs + elapsedMs
    return when {
        position < durationMs -> copy(positionMs = position)
        repeat == RepeatMode.One && !isStation -> copy(positionMs = 0)
        currentIndex < queue.lastIndex -> atTrack(currentIndex + 1)
        loops -> atTrack(0)
        else -> copy(positionMs = durationMs, isPlaying = false)
    }
}
