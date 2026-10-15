package com.illiamelnyk.studio.core.data.playback

import com.illiamelnyk.studio.core.domain.playback.AutoplayCatalog
import com.illiamelnyk.studio.core.domain.playback.AutoplayPolicy
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlaybackAutoplayCoordinator(
    playback: PlaybackController,
    catalog: AutoplayCatalog,
    scope: CoroutineScope,
    ownerId: () -> String?,
) {
    init {
        scope.launch {
            playback.state.map {
                it.copy(positionMs = 0, isPlaying = false)
            }.distinctUntilChanged().collectLatest { state ->
                val owner = ownerId()
                if (owner == null || !state.autoplay || state.isStation || state.current == null ||
                    state.currentIndex != state.queue.lastIndex || state.repeat != RepeatMode.Off
                ) {
                    return@collectLatest
                }
                try {
                    val next = AutoplayPolicy.next(catalog.tracks(), state) ?: return@collectLatest
                    if (ownerId() == owner) playback.appendAutoplay(state, next)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            }
        }
    }
}
