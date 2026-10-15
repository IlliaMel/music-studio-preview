package com.illiamelnyk.studio.core.domain.track

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ComposerDraftStore {
    private val mutableRevision = MutableStateFlow(0)
    val revision: StateFlow<Int> = mutableRevision.asStateFlow()
    private val mutablePending = MutableStateFlow<Track?>(null)
    val pending: StateFlow<Track?> = mutablePending.asStateFlow()

    fun remix(track: Track) {
        mutablePending.value = track
        mutableRevision.value += 1
    }
    fun clear() {
        mutablePending.value = null
    }
}
