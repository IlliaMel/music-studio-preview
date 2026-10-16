package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import kotlinx.coroutines.flow.first

/** Signs out. Playback stops first: nothing of the previous account keeps playing. */
class SignOut(
    private val sessions: SessionRepository,
    private val playback: PlaybackController,
    private val drafts: ComposerDraftStore? = null,
) {
    suspend operator fun invoke() {
        val owner = sessions.session.first()?.user?.id
        val draftOwner = drafts?.currentOwner
        playback.stopDurably()
        if (sessions.session.first()?.user?.id != owner) return
        drafts?.clearIfOwner(draftOwner)
        sessions.signOutIfCurrent(owner)
    }
}
