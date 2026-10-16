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
        val owner = sessions.session.first()?.user?.id
        val draftOwner = drafts?.currentOwner
        val ids = tracks.observeMine().first().map { it.id }
        val result = sessions.deleteAccount()
        val currentOwner = sessions.session.first()?.user?.id
        if ((result is Outcome.Success || currentOwner == null) && (currentOwner == null || currentOwner == owner)) {
            drafts?.clearIfOwner(draftOwner)
            playback.stopDurably()
            for (id in ids) {
                val active = sessions.session.first()?.user?.id
                if (active != null && active != owner) break
                tracks.delete(id)
            }
        }
        result
    }
}
