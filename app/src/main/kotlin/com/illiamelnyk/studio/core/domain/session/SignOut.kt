package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.playback.PlaybackController

/** Signs out. Playback stops first: nothing of the previous account keeps playing. */
class SignOut(private val sessions: SessionRepository, private val playback: PlaybackController) {
    suspend operator fun invoke() {
        playback.stop()
        sessions.signOut()
    }
}
