package com.illiamelnyk.studio.navigation

import android.util.Log
import com.illiamelnyk.studio.BuildConfig
import java.util.concurrent.atomic.AtomicReference

internal object NavigationRenderProbe {
    private data class Pending(val tab: TopLevelTab, val startedAtNanos: Long)
    private val pending = AtomicReference<Pending?>()

    fun request(tab: TopLevelTab) {
        if (BuildConfig.DEBUG) pending.set(Pending(tab, System.nanoTime()))
    }

    fun cancel() {
        if (BuildConfig.DEBUG) pending.set(null)
    }

    fun firstDraw(tab: TopLevelTab) {
        if (!BuildConfig.DEBUG) return
        val request = pending.get() ?: return
        if (request.tab != tab || !pending.compareAndSet(request, null)) return
        val milliseconds = (System.nanoTime() - request.startedAtNanos) / 1_000_000.0
        Log.d("StudioNavigation", "tab=${tab.name} renderSubmissionMs=$milliseconds")
    }
}
