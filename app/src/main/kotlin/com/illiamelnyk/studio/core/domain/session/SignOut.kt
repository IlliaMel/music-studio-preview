package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore

/** Signs out. Playback stops first: nothing of the previous account keeps playing. */
class SignOut(
    private val sessions: SessionRepository,
    private val playback: PlaybackController,
    private val drafts: ComposerDraftStore? = null,
) {
    suspend operator fun invoke() {
        playback.stop()
        drafts?.clear()
        sessions.signOut()
    }
}
