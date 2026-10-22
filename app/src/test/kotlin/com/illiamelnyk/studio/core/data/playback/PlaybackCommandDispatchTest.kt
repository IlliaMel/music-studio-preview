package com.illiamelnyk.studio.core.data.playback

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackCommandDispatchTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val playbackScopes = PlaybackTestScopes(main.testDispatcher.scheduler)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @org.junit.After
    fun closePlaybackScopes() {
        playbackScopes.close()
    }

    @Test
    fun pausedRestoreNeverStartsServiceAndLaterPlayBootstrapsItEvenWithAnActiveClock() = runPlaybackTest {
        val owner = "foreground-service-owner"
        val song = track("foreground-service-track", 20.seconds)
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val saved = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray().put(song.toJson())).put("order", JSONArray().put(0))
            .put("index", 0).put("position", 4000)
        check(preferences.edit().clear().putString(playbackPreferenceKey(owner), saved.toString()).commit())
        val wrapped = RecordingServiceContext(context)
        val playback = testPlaybackController(wrapped, playbackScopes.wrap(backgroundScope), { owner })
        try {
            runCurrent()
            assertFalse(playback.state.value.isPlaying)
            assertEquals(0, wrapped.ordinaryRequests)
            assertEquals(4000L, playback.state.value.positionMs)
            playback.togglePlayPause()
            runCurrent()
            assertEquals(1, wrapped.ordinaryRequests)
            playback.togglePlayPause()
            runCurrent()
            assertFalse(playback.state.value.isPlaying)
            assertEquals(1, wrapped.ordinaryRequests)
            // Position monitoring remains active during pause. It must not swallow service restart.
            playback.togglePlayPause()
            runCurrent()
            assertTrue(playback.state.value.isPlaying)
            assertEquals(2, wrapped.ordinaryRequests)
            playback.releaseForServiceShutdown()
            assertEquals(2, wrapped.ordinaryRequests)
            assertFalse(playback.state.value.isPlaying)
            assertEquals(0, wrapped.foregroundRequests)
        } finally {
            try {
                playback.stopDurably()
            } finally {
                playbackScopes.closeSuspending()
            }
        }
    }

    @Test
    fun immediateAuthorizationFailureHasNoForegroundStartupDeadline() = runPlaybackTest {
        val wrapped = RecordingServiceContext(context)
        val song = track("authorization-before-service", 20.seconds)
            .copy(audioUri = "https://private.example/audio")
        val playback = testPlaybackController(
            wrapped,
            playbackScopes.wrap(backgroundScope),
            { "auth-failure-owner" },
            audioHeaders = {
                throw com.illiamelnyk.studio.core.data.local.ApiFailure(
                    com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
                )
            },
        )
        try {
            playback.play(listOf(song))
            runCurrent()
            assertEquals(1, wrapped.ordinaryRequests)
            assertEquals(0, wrapped.foregroundRequests)
            assertFalse(playback.state.value.isPlaying)
            assertEquals(
                com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
                playback.state.value.failure?.error,
            )
        } finally {
            try {
                playback.stopDurably()
            } finally {
                playbackScopes.closeSuspending()
            }
        }
    }

    @Test
    fun quickPauseBeforeServiceCreationHasNoForegroundStartupDeadline() = runPlaybackTest {
        val wrapped = RecordingServiceContext(context)
        val playback = testPlaybackController(wrapped, playbackScopes.wrap(backgroundScope), { "quick-pause-owner" })
        try {
            playback.play(listOf(track("quick-pause", 20.seconds)))
            playback.togglePlayPause()
            runCurrent()
            assertFalse(playback.state.value.isPlaying)
            assertEquals(1, wrapped.ordinaryRequests)
            assertEquals(0, wrapped.foregroundRequests)
            playback.togglePlayPause()
            runCurrent()
            assertTrue(playback.state.value.isPlaying)
            assertEquals(2, wrapped.ordinaryRequests)
            assertEquals(0, wrapped.foregroundRequests)
        } finally {
            try {
                playback.stopDurably()
            } finally {
                playbackScopes.closeSuspending()
            }
        }
    }

    private class RecordingServiceContext(base: Context) : ContextWrapper(base) {
        var ordinaryRequests = 0
        var foregroundRequests = 0
        override fun startService(service: Intent): ComponentName {
            ordinaryRequests++
            return ComponentName(this, PlaybackService::class.java)
        }
        override fun startForegroundService(service: Intent): ComponentName {
            foregroundRequests++
            return ComponentName(this, PlaybackService::class.java)
        }
    }

    @Test
    fun ordinaryStopReturnsAndMainHeartbeatRunsWhileDiskCommitIsBlocked() = runPlaybackTest {
        withBlockedCommit("ordinary-stop", backgroundScope) { playback, entered, release ->
            playback.setAutoplay(true)
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            val command = async(Dispatchers.Main) { playback.stop() }
            val heartbeat = async(Dispatchers.Main) { "main advanced" }
            runCurrent()
            assertTrue(command.isCompleted)
            assertEquals("main advanced", heartbeat.await())
            assertNull(playback.state.value.current)
            assertEquals(1L, release.count)
        }
    }

    @Test
    fun serviceReleaseReturnsPausedSnapshotAndMainHeartbeatBeforeCommitCompletes() = runPlaybackTest {
        withBlockedCommit("ordinary-release", backgroundScope) { playback, entered, release ->
            playback.setAutoplay(true)
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            playback.seekTo(8500)
            val command = async(Dispatchers.Main) { playback.releaseForServiceShutdown() }
            val heartbeat = async(Dispatchers.Main) { "main advanced" }
            runCurrent()
            assertTrue(command.isCompleted)
            assertEquals("main advanced", heartbeat.await())
            assertEquals("dispatch-first", playback.state.value.current?.id?.value)
            assertEquals(8500L, playback.state.value.positionMs)
            assertFalse(playback.state.value.isPlaying)
            assertEquals(1L, release.count)
        }
    }

    @Test
    fun removeTrackReturnsUpdatedQueueAndMainHeartbeatBeforeCommitCompletes() = runPlaybackTest {
        withBlockedCommit("ordinary-remove", backgroundScope) { playback, entered, release ->
            playback.setAutoplay(true)
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            val upcoming = playback.state.value.queue.last().id
            val command = async(Dispatchers.Main) { playback.removeTrack(upcoming) }
            val heartbeat = async(Dispatchers.Main) { "main advanced" }
            runCurrent()
            assertTrue(command.isCompleted)
            assertEquals("main advanced", heartbeat.await())
            assertEquals(listOf("dispatch-first"), playback.state.value.queue.map { it.id.value })
            assertEquals(4000L, playback.state.value.positionMs)
            assertEquals(1L, release.count)
        }
    }

    private suspend fun withBlockedCommit(
        owner: String,
        scope: kotlinx.coroutines.CoroutineScope,
        block: suspend (PlaybackController, CountDownLatch, CountDownLatch) -> Unit,
    ) {
        val controllerScope = playbackScopes.wrap(scope)
        PlaybackPersistenceWriter.shared.flush()
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val songs = listOf(track("dispatch-first", 20.seconds), track("dispatch-second", 20.seconds))
        val saved = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray(songs.map { it.toJson() })).put("order", JSONArray(listOf(0, 1)))
            .put("index", 0).put("position", 4000).put("shuffle", false).put("repeat", "Off")
        check(preferences.edit().clear().putString(playbackPreferenceKey(owner), saved.toString()).commit())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val shared = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        return editor.commit()
                    }
                }
            }
        }
        val wrapped = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "playback") shared else super.getSharedPreferences(name, mode)
        }
        val playback = testPlaybackController(wrapped, controllerScope, ownerId = { owner })
        try {
            block(playback, entered, release)
        } finally {
            release.countDown()
            playback.stopDurably()
        }
    }
    private fun runPlaybackTest(test: suspend TestScope.() -> Unit) = runTest {
        try {
            test()
        } finally {
            playbackScopes.closeSuspending()
        }
    }
}
