package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DeleteAccount(
    private val sessions: SessionRepository,
    private val playback: PlaybackController,
    private val tracks: TrackRepository,
    private val drafts: ComposerDraftStore? = null,
) {
    suspend operator fun invoke(): Outcome<Unit> = withContext(NonCancellable) {
        val ids = tracks.observeMine().first().map { it.id }
        val result = sessions.deleteAccount()
        if (result is Outcome.Success || sessions.session.first() == null) {
            drafts?.clear()
            playback.stop()
            ids.forEach { tracks.delete(it) }
        }
        result
    }
}
