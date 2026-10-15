package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Clears local fake tracks after account deletion succeeds and stops playback. */
class DeleteAccount(
    private val sessions: SessionRepository,
    private val playback: PlaybackController,
    private val tracks: TrackRepository,
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val ids = tracks.observeMine().first().map { it.id }
        val result = sessions.deleteAccount()
        if (result is Outcome.Success) {
            // Session removal disposes the settings ViewModel; finish local cleanup regardless.
            withContext(NonCancellable) {
                playback.stop()
                ids.forEach { tracks.delete(it) }
            }
        }
        return result
    }
}
