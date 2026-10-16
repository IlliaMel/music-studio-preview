package com.illiamelnyk.studio.core.data.playback

import com.illiamelnyk.studio.core.data.local.ApiFailure
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.data.local.toTrack
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.PlaybackFailure
import com.illiamelnyk.studio.core.domain.playback.PlaybackResumption
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidPlaybackController(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val ownerId: () -> String?,
    private val random: Random = Random.Default,
    private val audioHeaders: suspend (Track) -> Map<String, String> = { it.audioHeaders },
) : PlaybackController {
    private val playbackLock = Any()
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private var failureSequence = 0L
    private var playbackOwner: String? = null
    private var playbackGeneration = 0L
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
                    mutableState.update { it.copy(isPlaying = false, autoplayWaiting = false) }
                }

                android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.2f, 0.2f)

                android.media.AudioManager.AUDIOFOCUS_GAIN -> player?.setVolume(1f, 1f)
            }
        }.build()
    private val preferences = context.getSharedPreferences("playback", android.content.Context.MODE_PRIVATE)

    private val persistence = PlaybackStatePersistence(preferences, ownerId)
    private val persistenceEpoch = java.util.concurrent.atomic.AtomicLong()

    init {
        clearLegacyState()
        restoreQueue()
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            playbackPurgeGenerations.collect { generations ->
                synchronized(playbackLock) {
                    val owner = playbackOwner
                    if (owner != null && playbackGeneration != (generations[owner] ?: 0L)) stop()
                }
            }
        }
        scope.launch {
            var lastSaved: String? = null
            mutableState.collect { observed ->
                synchronized(playbackLock) {
                    if (observed === mutableState.value) {
                        val saved = snapshot(observed)
                        if (saved != null && (saved.second != lastSaved || !persistence.contains(saved.first))) {
                            persistQueue(saved)
                            lastSaved = saved.second
                        }
                    }
                }
            }
        }
    }

    private fun clearLegacyState() {
        if (persistence.contains("state")) {
            persistence.remove(listOf("state"))
            persistence.flush()
        }
    }

    private fun restoreQueue() {
        val owner = ownerId() ?: return
        val key = playbackPreferenceKey(owner)
        var observed: Any? = null
        var generation = playbackStateGeneration(owner)
        runCatching {
            synchronized(playbackPersistenceLock) {
                generation = playbackStateGeneration(owner)
                observed = persistence.readValue(key)
                val saved = observed?.let { org.json.JSONObject(it as String) } ?: return@runCatching
                require(saved.getInt("version") == 2 && saved.getString("owner") == owner)
                val array = saved.getJSONArray("original")
                val original = (0 until array.length()).map { array.getJSONObject(it).toTrack() }
                val order = saved.getJSONArray("order").let { values ->
                    (0 until values.length()).map(values::getInt)
                }
                require(original.isNotEmpty() && order.sorted() == original.indices.toList())
                val index = saved.getInt("index")
                require(index in order.indices)
                originalQueue = original
                playOrder = order
                playbackOwner = owner
                playbackGeneration = playbackStateGeneration(owner)
                val duration = original[order[index]].duration.inWholeMilliseconds
                mutableState.value = PlaybackState(
                    queue = order.map(original::get),
                    currentIndex = index,
                    positionMs = saved.optLong("position").coerceIn(0, duration),
                    shuffle = saved.optBoolean("shuffle"),
                    repeat = RepeatMode.entries.firstOrNull { it.name == saved.optString("repeat") } ?: RepeatMode.Off,
                    stationName = saved.optString("station").takeIf { it.isNotBlank() && it != "null" },
                    autoplay = saved.optBoolean("autoplay"),
                    autoplayHistory = saved.optJSONArray("autoplayHistory")?.let { values ->
                        (0 until values.length()).map { TrackId(values.getString(it)) }.toSet()
                    } ?: original.map { it.id }.toSet(),
                )
                val transferring = persistence.pending(key) || persistence.pending(autoplayPreferenceKey(owner))
                persistence.claim(owner)
                if (transferring) {
                    persistAutoplay()
                    persistQueue()
                }
            }
        }.onFailure {
            persistence.removeUnclaimed(owner, generation, observed)
            persistence.flush()
            android.util.Log.w("StudioPlayback", "Discarded invalid or mismatched playback state")
        }
    }

    private fun snapshot(state: PlaybackState): Pair<String, String>? {
        val owner = playbackOwner ?: return null
        if (owner != ownerId() || playbackGeneration != playbackStateGeneration(owner) || state.current == null) {
            return null
        }
        val saved = org.json.JSONObject().apply {
            put("version", 2)
            put("owner", owner)
            put("original", org.json.JSONArray(originalQueue.map { it.toJson() }))
            put("order", org.json.JSONArray(playOrder))
            put("index", state.currentIndex)
            put("position", state.positionMs / 1000 * 1000)
            put("shuffle", state.shuffle)
            put("repeat", state.repeat.name)
            put("station", state.stationName)
            put("autoplay", state.autoplay)
            put("autoplayHistory", org.json.JSONArray(state.autoplayHistory.map { it.value }))
        }
        return playbackPreferenceKey(owner) to saved.toString()
    }

    private fun persistQueue(saved: Pair<String, String>? = snapshot(mutableState.value)) {
        val owner = playbackOwner ?: return
        val snapshot = saved ?: return
        val epoch = persistenceEpoch.get()
        persistence.write(snapshot.first, snapshot.second, owner, playbackGeneration) {
            persistenceEpoch.get() == epoch
        }
    }

    private fun persistAutoplay() {
        val owner = playbackOwner ?: return
        val state = mutableState.value
        val saved = org.json.JSONObject().apply {
            put("enabled", state.autoplay)
            put("history", org.json.JSONArray(state.autoplayHistory.map { it.value }))
        }
        val epoch = persistenceEpoch.get()
        persistence.write(autoplayPreferenceKey(owner), saved.toString(), owner, playbackGeneration) {
            persistenceEpoch.get() == epoch
        }
    }

    private fun autoplayPreference(owner: String): Pair<Boolean, Set<TrackId>> = runCatching {
        val saved = persistence.read(autoplayPreferenceKey(owner))?.let { org.json.JSONObject(it) }
            ?: return@runCatching false to emptySet<TrackId>()
        saved.optBoolean("enabled") to (
            saved.optJSONArray("history")?.let { values ->
                (0 until values.length()).map { TrackId(values.getString(it)) }.toSet()
            } ?: emptySet()
            )
    }.getOrDefault(false to emptySet())

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
                mutableState.update { it.copy(isPlaying = false, autoplayWaiting = false) }
            } else {
                if (!prepared) loadedId = null
                val atEnd = state.positionMs >= state.durationMs
                if (atEnd) pendingSeek = 0
                mutableState.update {
                    val position = if (atEnd) 0 else it.positionMs
                    it.copy(isPlaying = true, positionMs = position, autoplayWaiting = false, failure = null)
                }
                startClock()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        synchronized(playbackLock) {
            pendingSeek = positionMs.coerceIn(0, mutableState.value.durationMs)
            mutableState.update { it.copy(positionMs = pendingSeek!!, autoplayWaiting = false) }
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
                    state.positionMs > RESTART_THRESHOLD.inWholeMilliseconds -> state.copy(
                        positionMs = 0,
                        autoplayWaiting = false,
                    )

                    state.currentIndex > 0 -> state.atTrack(state.currentIndex - 1)

                    state.loops && state.queue.isNotEmpty() -> state.atTrack(state.queue.lastIndex)

                    else -> state.copy(positionMs = 0, autoplayWaiting = false)
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
            mutableState.update { it.copy(repeat = mode, autoplayWaiting = false) }
        }
    }

    override fun setAutoplay(enabled: Boolean) {
        synchronized(playbackLock) {
            val state = mutableState.value
            if (state.isStation || state.current == null || playbackOwner != ownerId() ||
                playbackOwner?.let { playbackGeneration == playbackStateGeneration(it) } != true
            ) {
                return
            }
            mutableState.value = state.copy(autoplay = enabled, autoplayWaiting = false)
            persistAutoplay()
            persistQueue()
        }
    }

    override fun appendAutoplay(expected: PlaybackState, track: Track) {
        synchronized(playbackLock) {
            val state = mutableState.value
            if (!state.autoplay || state.isStation || state.repeat != RepeatMode.Off ||
                state.current?.id != expected.current?.id || state.currentIndex != expected.currentIndex ||
                state.queue.map { it.id } != expected.queue.map { it.id } ||
                state.autoplayHistory != expected.autoplayHistory || track.id in state.autoplayHistory ||
                state.queue.any { it.id == track.id } || playbackOwner != ownerId() ||
                playbackOwner?.let { playbackGeneration == playbackStateGeneration(it) } != true
            ) {
                return
            }
            val index = originalQueue.size
            originalQueue = originalQueue + track
            playOrder = playOrder + index
            val resume = state.autoplayWaiting
            mutableState.value = state.copy(
                queue = playOrder.map(originalQueue::get),
                autoplayHistory = state.autoplayHistory + track.id,
                autoplayWaiting = false,
                currentIndex = if (resume) state.currentIndex + 1 else state.currentIndex,
                positionMs = if (resume) 0 else state.positionMs,
                isPlaying = state.isPlaying || resume,
            )
            if (resume) startClock()
            persistAutoplay()
            persistQueue()
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

    override fun removeTrack(id: TrackId) {
        synchronized(playbackLock) {
            val state = mutableState.value
            val current = state.current ?: return
            if (originalQueue.none { it.id == id }) return
            val retained = originalQueue.indices.filter { originalQueue[it].id != id }
            if (retained.isEmpty()) {
                stop()
                return@synchronized
            }
            val remap = retained.mapIndexed { index, old -> old to index }.toMap()
            val currentOriginal = playOrder[state.currentIndex]
            val order = playOrder.mapNotNull(remap::get)
            val currentRemoved = current.id == id
            val nextIndex = if (currentRemoved) {
                state.currentIndex.coerceAtMost(order.lastIndex)
            } else {
                order.indexOf(remap.getValue(currentOriginal))
            }
            if (currentRemoved) {
                preparing?.cancel()
                player?.release()
                player = null
                loadedId = null
                prepared = false
                pendingSeek = 0
                if (!state.isPlaying) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    hasFocus = false
                }
            }
            originalQueue = retained.map(originalQueue::get)
            playOrder = order
            mutableState.value = state.copy(
                queue = order.map(originalQueue::get),
                currentIndex = nextIndex,
                positionMs = if (currentRemoved) 0 else state.positionMs,
            )
            persistQueue()
        }
        flushPersistence()
    }

    override fun clearUpNext() {
        synchronized(playbackLock) {
            val current = mutableState.value.current ?: return
            originalQueue = listOf(current)
            playOrder = listOf(0)
            mutableState.update { it.copy(queue = originalQueue, currentIndex = 0) }
        }
    }

    override fun systemSnapshot(): PlaybackResumption? = synchronized(playbackLock) {
        val owner = playbackOwner ?: return@synchronized null
        val state = mutableState.value
        val track = state.current ?: return@synchronized null
        if (owner != ownerId() || playbackGeneration != playbackStateGeneration(owner) || !persistence.owns(owner)) {
            return@synchronized null
        }
        val identity = owner + ":" + playbackGeneration + ":" + track.id.value
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        PlaybackResumption(digest.joinToString("") { "%02x".format(it.toInt() and 255) }, state)
    }

    override fun releaseForServiceShutdown() {
        synchronized(playbackLock) {
            val actual = if (prepared) runCatching { player?.currentPosition?.toLong() }.getOrNull() else null
            val position = pendingSeek ?: actual
            preparing?.cancel()
            player?.release()
            player = null
            prepared = false
            loadedId = null
            pendingSeek = null
            stopClock()
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
            mutableState.update {
                it.copy(isPlaying = false, positionMs = position ?: it.positionMs, autoplayWaiting = false)
            }
            persistQueue()
        }
        flushPersistence()
    }

    override fun stop() {
        val nested = Thread.holdsLock(playbackLock)
        stopInternal()
        if (!nested) flushPersistence()
    }

    override suspend fun stopDurably() {
        withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { stopInternal() }
        withContext(kotlinx.coroutines.Dispatchers.IO) { flushPersistence() }
    }

    private fun stopInternal() {
        synchronized(playbackLock) {
            persistenceEpoch.incrementAndGet()
            persistAutoplay()
            preparing?.cancel()
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
            player?.release()
            player = null
            loadedId = null
            prepared = false
            pendingSeek = null
            stopClock()
            if (playbackOwner == ownerId() && playbackOwner?.let(persistence::owns) == true) {
                context.stopService(android.content.Intent(context, PlaybackService::class.java))
            }
            originalQueue = emptyList()
            playOrder = emptyList()
            mutableState.update { PlaybackState(shuffle = it.shuffle, repeat = it.repeat) }
            playbackOwner?.let { persistence.removeQueue(it, playbackGeneration) }
            playbackOwner = null
        }
    }

    private fun flushPersistence() {
        check(!Thread.holdsLock(playbackLock)) { "Playback persistence cannot flush under playback lock" }
        persistence.flush()
    }

    private fun load(queue: List<Track>, startIndex: Int, stationName: String?) {
        require(startIndex in queue.indices) { "Start index $startIndex is outside the queue of ${queue.size}" }
        val owner = ownerId() ?: return
        if (playbackOwner != null && playbackOwner != owner) stop()
        playbackOwner = owner
        playbackGeneration = playbackStateGeneration(owner)
        persistence.claim(owner)
        pendingSeek = 0
        originalQueue = queue
        val autoplay = autoplayPreference(owner)
        val shuffle = mutableState.value.shuffle
        playOrder = if (shuffle) shuffledOrder(first = startIndex) else queue.indices.toList()
        mutableState.update {
            it.copy(
                queue = playOrder.map(queue::get),
                currentIndex = if (shuffle) 0 else startIndex,
                positionMs = 0,
                isPlaying = true,
                stationName = stationName,
                autoplayWaiting = false,
                failure = null,
                autoplay = autoplay.first,
                autoplayHistory = autoplay.second + queue.map { track -> track.id },
            )
        }
        persistAutoplay()
        persistQueue()
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
        val owner = playbackOwner
        if (owner != null &&
            (owner != ownerId() || playbackGeneration != playbackStateGeneration(owner) || !persistence.owns(owner))
        ) {
            stop()
            return
        }
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
            val identity = systemSnapshot()?.mediaId
            preparing = scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
                try {
                    val headers = audioHeaders(track)
                    val uri = AudioFiles(context).playableUri(track)
                    if (identity == null || systemSnapshot()?.mediaId != identity || loadedId != track.id.value) {
                        return@launch
                    }
                    val next = android.media.MediaPlayer()
                    player = next
                    next.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                    next.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    )
                    next.setDataSource(context, uri, headers)
                    next.setOnPreparedListener {
                        if (!ownsCurrentPlayer(next, track)) {
                            return@setOnPreparedListener
                        }
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
                        if (!ownsCurrentPlayer(next, track)) {
                            return@setOnCompletionListener
                        }
                        val before = mutableState.value
                        val after = before.copy(positionMs = before.durationMs).advancedBy(1)
                        mutableState.value = after
                        if (after.current?.id == before.current?.id && after.isPlaying) {
                            next.seekTo(0)
                            next.start()
                        }
                    }
                    next.setOnErrorListener { _, what, extra ->
                        if (ownsCurrentPlayer(next, track)) {
                            android.util.Log.e("StudioPlayback", "Audio error: $what/$extra")
                            next.release()
                            player = null
                            prepared = false
                            val failure = when {
                                extra == android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT -> AppError.Timeout

                                extra == android.media.MediaPlayer.MEDIA_ERROR_IO && track.isRemoteAudio() -> {
                                    AppError.Network
                                }

                                else -> AppError.Unknown
                            }
                            reportFailure(track, failure)
                        }
                        true
                    }
                    next.prepareAsync()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    if (loadedId == track.id.value && mutableState.value.current?.id == track.id &&
                        playbackOwner == ownerId() &&
                        playbackOwner?.let { playbackGeneration == playbackStateGeneration(it) } == true
                    ) {
                        android.util.Log.e("StudioPlayback", "Cannot open audio", error)
                        player?.release()
                        player = null
                        prepared = false
                        val failure = when (error) {
                            is ApiFailure -> error.error
                            is java.net.SocketTimeoutException -> AppError.Timeout
                            is java.io.IOException -> if (track.isRemoteAudio()) AppError.Network else AppError.Unknown
                            else -> AppError.Unknown
                        }
                        reportFailure(track, failure)
                    }
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

    private fun reportFailure(track: Track, error: AppError) {
        synchronized(playbackLock) {
            val owner = playbackOwner ?: return
            val activeOwner = owner == ownerId() && playbackGeneration == playbackStateGeneration(owner)
            if (!activeOwner || !persistence.owns(owner) || mutableState.value.current?.id != track.id) {
                return
            }
            failureSequence++
            mutableState.update {
                it.copy(
                    isPlaying = false,
                    autoplayWaiting = false,
                    failure = PlaybackFailure(failureSequence, track.id, error),
                )
            }
        }
    }

    private fun ownsCurrentPlayer(candidate: android.media.MediaPlayer, track: Track): Boolean =
        player === candidate && mutableState.value.current?.id == track.id && playbackOwner == ownerId() &&
            playbackOwner?.let { playbackGeneration == playbackStateGeneration(it) && persistence.owns(it) } == true

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) mutableState.update { it.copy(isPlaying = false, autoplayWaiting = false) }
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

private fun PlaybackState.atTrack(index: Int) = copy(
    currentIndex = index,
    positionMs = 0,
    autoplayWaiting = false,
    failure = null,
)

private fun PlaybackState.advancedBy(elapsedMs: Long): PlaybackState {
    if (!isPlaying || current == null) return this
    val position = positionMs + elapsedMs
    return when {
        position < durationMs -> copy(positionMs = position)
        repeat == RepeatMode.One && !isStation -> copy(positionMs = 0)
        currentIndex < queue.lastIndex -> atTrack(currentIndex + 1)
        loops -> atTrack(0)
        else -> copy(positionMs = durationMs, isPlaying = false, autoplayWaiting = autoplay && !isStation)
    }
}

private fun Track.isRemoteAudio(): Boolean {
    val uri = audioUri ?: return false
    return uri.startsWith("https://") || uri.startsWith("http://")
}
