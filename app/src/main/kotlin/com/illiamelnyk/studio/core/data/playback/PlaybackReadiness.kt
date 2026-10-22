package com.illiamelnyk.studio.core.data.playback

/** Suspends until local startup has published its authoritative owner; never blocks the main thread. */
internal fun interface PlaybackReadiness {
    suspend fun awaitReady()
}
