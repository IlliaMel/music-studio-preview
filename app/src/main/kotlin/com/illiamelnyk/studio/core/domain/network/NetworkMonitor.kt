package com.illiamelnyk.studio.core.domain.network

import kotlinx.coroutines.flow.Flow

/** Connectivity shared by the app and future network-backed repositories. */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
    fun refresh()
}
