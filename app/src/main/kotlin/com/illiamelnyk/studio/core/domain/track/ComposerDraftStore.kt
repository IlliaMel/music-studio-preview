package com.illiamelnyk.studio.core.domain.track

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ComposerDraftStore(private val ownerId: () -> String? = { null }) {
    val currentOwner: String? get() = checkOwner()
    private var owner: String? = null
    private val mutableRevision = MutableStateFlow(0)
    val revision: StateFlow<Int> = owned(mutableRevision)
    private val mutablePending = MutableStateFlow<Track?>(null)
    val pending: StateFlow<Track?> = owned(mutablePending)
    private val mutableSources = MutableStateFlow<List<Track>>(emptyList())
    val sources: StateFlow<List<Track>> = owned(mutableSources)
    private val mutableProblem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = owned(mutableProblem)

    @Synchronized
    fun remix(track: Track) {
        if (checkOwner() == null) return
        mutableRevision.value += 1
        mutableProblem.value = when {
            track.id.isRecording -> Problem.RecordingReference

            track.isRemix -> Problem.AlreadyRemixed

            track.status != TrackStatus.Ready -> Problem.NotReady

            mutableSources.value.any { it.id == track.id } -> null

            mutableSources.value.size >= MAX_SOURCES -> Problem.TooMany

            else -> {
                mutableSources.value = mutableSources.value + track
                mutablePending.value = track
                null
            }
        }
    }

    @Synchronized
    fun remove(id: TrackId) {
        if (checkOwner() == null) return
        mutableSources.value = mutableSources.value.filterNot { it.id == id }
        mutablePending.value = mutableSources.value.lastOrNull()
    }

    @Synchronized
    fun clear() {
        owner = ownerId()
        reset()
    }

    fun refreshOwner() {
        checkOwner()
    }

    @Synchronized
    fun problemShown() {
        checkOwner()
        mutableProblem.value = null
    }

    @Synchronized
    private fun checkOwner(): String? {
        val current = ownerId()
        if (current != owner) {
            owner = current
            reset()
        }
        return current
    }

    private fun reset() {
        mutableSources.value = emptyList()
        mutablePending.value = null
        mutableProblem.value = null
        mutableRevision.value = 0
    }

    private fun <T> owned(state: StateFlow<T>): StateFlow<T> = object : StateFlow<T> {
        override val value: T get() {
            checkOwner()
            return state.value
        }
        override val replayCache: List<T> get() = listOf(value)

        @OptIn(InternalCoroutinesApi::class)
        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            checkOwner()
            return state.collect(object : FlowCollector<T> {
                override suspend fun emit(value: T) {
                    checkOwner()
                    collector.emit(state.value)
                }
            })
        }
    }

    enum class Problem { TooMany, AlreadyRemixed, NotReady, RecordingReference }
    companion object {
        const val MAX_SOURCES = 4
    }
}
