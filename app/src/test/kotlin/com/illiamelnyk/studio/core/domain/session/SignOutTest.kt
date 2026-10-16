package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutTest {
    @Test
    fun signOutClearsRemixSourcesBeforeRemovingTheSession() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("demo", SignInMethod.Google, "Demo listener", useProviderProfile = true)
        val song = track("private-source", 20.seconds)
        val drafts = ComposerDraftStore { "test-owner" }.apply { remix(song) }
        val playback = FakePlaybackController(backgroundScope)
        playback.play(listOf(song))
        var clearedBeforeSignOut = false
        val observed = object : SessionRepository by sessions {
            override suspend fun signOut() {
                clearedBeforeSignOut = drafts.sources.value.isEmpty() && drafts.pending.value == null &&
                    playback.state.value.current == null
                sessions.signOut()
            }
        }
        SignOut(observed, playback, drafts)()
        assertTrue(clearedBeforeSignOut)
        assertNull(sessions.session.first())
        sessions.signIn("other", SignInMethod.Google, "Another listener", useProviderProfile = true)
        assertTrue(drafts.sources.value.isEmpty())
        assertNull(drafts.pending.value)
    }
}
