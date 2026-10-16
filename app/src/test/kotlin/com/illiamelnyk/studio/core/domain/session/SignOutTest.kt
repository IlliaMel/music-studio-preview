package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.fake.FakeSessionRepository
import com.illiamelnyk.studio.core.domain.track.ComposerDraftStore
import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
            override suspend fun signOutIfCurrent(expectedOwner: UserId?): Boolean {
                clearedBeforeSignOut = drafts.sources.value.isEmpty() && drafts.pending.value == null &&
                    playback.state.value.current == null
                return sessions.signOutIfCurrent(expectedOwner)
            }
        }
        SignOut(observed, playback, drafts)()
        assertTrue(clearedBeforeSignOut)
        assertNull(sessions.session.first())
        sessions.signIn("other", SignInMethod.Google, "Another listener", useProviderProfile = true)
        assertTrue(drafts.sources.value.isEmpty())
        assertNull(drafts.pending.value)
    }

    @Test
    fun sessionAndSourcesRemainUntilDurableStopCompletes() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("first", SignInMethod.Google, "First", useProviderProfile = true)
        val owner = sessions.session.first()!!.user.id
        val song = track("durability-source", 20.seconds)
        val drafts = ComposerDraftStore { owner.value }.apply { remix(song) }
        val native = FakePlaybackController(backgroundScope).apply { play(listOf(song)) }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val playback = object : com.illiamelnyk.studio.core.domain.playback.PlaybackController by native {
            override suspend fun stopDurably() {
                native.stop()
                started.complete(Unit)
                release.await()
            }
        }
        val signingOut = async { SignOut(sessions, playback, drafts)() }
        runCurrent()
        started.await()
        assertFalse(signingOut.isCompleted)
        assertEquals(owner, sessions.session.first()!!.user.id)
        assertEquals(listOf(song), drafts.sources.value)
        assertNull(native.state.value.current)
        release.complete(Unit)
        signingOut.await()
        assertNull(sessions.session.first())
        assertTrue(drafts.sources.value.isEmpty())
    }

    @Test
    fun replacementOwnerAndSourcesSurviveOldDurableStopCompletion() = runTest {
        val sessions = FakeSessionRepository()
        sessions.signIn("first", SignInMethod.Google, "First", useProviderProfile = true)
        var draftOwner = sessions.session.first()!!.user.id.value
        val first = track("old-source", 20.seconds)
        val drafts = ComposerDraftStore { draftOwner }.apply { remix(first) }
        val native = FakePlaybackController(backgroundScope).apply { play(listOf(first)) }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val playback = object : com.illiamelnyk.studio.core.domain.playback.PlaybackController by native {
            override suspend fun stopDurably() {
                native.stop()
                started.complete(Unit)
                release.await()
            }
        }
        val signingOut = async { SignOut(sessions, playback, drafts)() }
        runCurrent()
        started.await()
        sessions.signIn("replacement", SignInMethod.Google, "Replacement", useProviderProfile = true)
        val replacementOwner = sessions.session.first()!!.user.id
        draftOwner = replacementOwner.value
        val replacement = track("replacement-source", 20.seconds)
        drafts.remix(replacement)
        native.play(listOf(replacement))
        release.complete(Unit)
        signingOut.await()
        assertEquals(replacementOwner, sessions.session.first()!!.user.id)
        assertEquals(listOf(replacement), drafts.sources.value)
        assertEquals(replacement.id, native.state.value.current?.id)
    }
}
