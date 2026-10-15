package com.illiamelnyk.studio.core.presentation

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-wide feedback and its timer, independent of whichever window is currently on top. */
class ToastController(private val scope: CoroutineScope) {
    private val sequence = AtomicLong()
    private val current = MutableStateFlow<ToastMessage?>(null)
    val message = current.asStateFlow()
    private val hosts = linkedMapOf<Long, Int>()
    private val active = MutableStateFlow<Long?>(null)
    val activeHost = active.asStateFlow()
    private var expiry: Job? = null

    @Synchronized
    fun show(title: UiText, detail: UiText? = null, isError: Boolean = false) {
        val id = sequence.incrementAndGet()
        expiry?.cancel()
        current.value = ToastMessage(id, title, detail, isError)
        expiry = scope.launch {
            delay(TOAST_DURATION_MS)
            dismiss(id)
        }
    }

    /** Highest window depth wins; creation order breaks ties between sibling windows. */
    @Synchronized
    fun registerHost(priority: Int = 0): Long {
        val id = sequence.incrementAndGet()
        hosts[id] = priority
        updateActiveHost()
        return id
    }

    @Synchronized
    fun unregisterHost(id: Long) {
        hosts.remove(id)
        updateActiveHost()
    }

    private fun updateActiveHost() {
        active.value = hosts.entries.maxWithOrNull(compareBy<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })?.key
    }

    /** An expired timer must never dismiss a newer message. */
    fun dismiss(id: Long) {
        val visible = current.value
        if (visible?.id == id) current.compareAndSet(visible, null)
    }

    private companion object {
        const val TOAST_DURATION_MS = 4_000L
    }
}

data class ToastMessage(val id: Long, val title: UiText, val detail: UiText?, val isError: Boolean)
