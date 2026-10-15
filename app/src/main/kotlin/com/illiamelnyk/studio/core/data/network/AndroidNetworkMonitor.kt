package com.illiamelnyk.studio.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.illiamelnyk.studio.core.domain.network.NetworkMonitor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Registers only while collected; losing a screen never leaks a connectivity callback. */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val refreshes =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val isOnline = merge(
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(online())
                }
                override fun onLost(network: Network) {
                    trySend(online())
                }
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    trySend(online())
                }
            }
            manager.registerDefaultNetworkCallback(callback)
            trySend(online())
            awaitClose { manager.unregisterNetworkCallback(callback) }
        },
        refreshes.map { online() },
    ).distinctUntilChanged()

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private fun online(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}
