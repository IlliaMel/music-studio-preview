package com.illiamelnyk.studio.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illiamelnyk.studio.core.domain.network.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/** Recovery is automatic: the same stream updates when Android validates a network again. */
class OfflineViewModel(private val monitor: NetworkMonitor) : ViewModel() {
    val isOnline = monitor.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)
    fun retry() = monitor.refresh()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
