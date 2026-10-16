package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackDurableStopTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val playbackScopes = PlaybackTestScopes(main.testDispatcher.scheduler)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @org.junit.After
    fun closePlaybackScopes() {
        playbackScopes.close()
    }

    @Test
    fun durableStopKeepsNativeStopOnMainWhileBlockedCommitAllowsMainWork() = runTest {
        val owner = "durable-stop-main"
        val preferences = restoredQueue(owner)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stoppedThread = AtomicReference<Thread?>()
        val commitThread = AtomicReference<Thread?>()
        val expectedMain = Thread.currentThread()
        val wrapped = wrapped(preferences, stoppedThread) {
            commitThread.set(Thread.currentThread())
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            true
        }
        val playback = AndroidPlaybackController(wrapped, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        val stopping = async { playback.stopDurably() }
        try {
            runCurrent()
            assertTrue(withContext(Dispatchers.IO) { entered.await(2, TimeUnit.SECONDS) })
            assertNull(playback.state.value.current)
            assertSame(expectedMain, stoppedThread.get())
            assertFalse(expectedMain === commitThread.get())
            assertFalse(stopping.isCompleted)
            val heartbeat = async(Dispatchers.Main) { "main remains responsive" }
            runCurrent()
            assertEquals("main remains responsive", heartbeat.await())
        } finally {
            release.countDown()
        }
        stopping.await()
        assertFalse(preferences.contains(playbackPreferenceKey(owner)))
    }

    @Test
    fun durableStopReportsCheckedCommitFailureAfterClearingNativeState() = runTest {
        val owner = "durable-stop-failure"
        val preferences = restoredQueue(owner)
        val stoppedThread = AtomicReference<Thread?>()
        val playback =
            AndroidPlaybackController(
                wrapped(preferences, stoppedThread) {
                    false
                },
                playbackScopes.wrap(backgroundScope),
                ownerId = { owner },
            )
        val failure = runCatching { playback.stopDurably() }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertNull(playback.state.value.current)
        assertSame(Thread.currentThread(), stoppedThread.get())
    }

    @Test
    fun failedDurableStopRetryRemovesTheOriginalPersistedQueue() = runTest {
        val owner = "durable-retry-original"
        val preferences = restoredQueue(owner)
        val allowCommit = AtomicBoolean(false)
        val playback =
            AndroidPlaybackController(
                wrapped(preferences, AtomicReference()) {
                    allowCommit.get()
                },
                playbackScopes.wrap(backgroundScope),
                ownerId = { owner },
            )
        assertTrue(runCatching { playback.stopDurably() }.exceptionOrNull() is IOException)
        assertTrue(preferences.contains(playbackPreferenceKey(owner)))
        allowCommit.set(true)
        playback.stopDurably()
        assertFalse(preferences.contains(playbackPreferenceKey(owner)))
    }

    @Test
    fun failedOldControllerRetryCannotRemoveReplacementControllersQueue() = runTest {
        val owner = "durable-retry-replacement"
        val preferences = restoredQueue(owner)
        val allowCommit = AtomicBoolean(false)
        val wrapped = wrapped(preferences, AtomicReference()) { allowCommit.get() }
        val old = AndroidPlaybackController(wrapped, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        assertTrue(runCatching { old.stopDurably() }.exceptionOrNull() is IOException)
        allowCommit.set(true)
        val replacement = AndroidPlaybackController(wrapped, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        replacement.seekTo(12000)
        replacement.releaseForServiceShutdown()
        old.stopDurably()
        val saved = JSONObject(requireNotNull(preferences.getString(playbackPreferenceKey(owner), null)))
        assertEquals(12000L, saved.getLong("position"))
        assertEquals("durable-stop-track", replacement.state.value.current?.id?.value)
    }

    @Test
    fun failedRemovalCannotEraseFreshSameOwnerPlaybackOnTheSameController() = runTest {
        val owner = "durable-retry-same-controller"
        val preferences = restoredQueue(owner)
        val allowCommit = AtomicBoolean(false)
        val playback =
            AndroidPlaybackController(
                wrapped(preferences, AtomicReference()) {
                    allowCommit.get()
                },
                playbackScopes.wrap(backgroundScope),
                ownerId = { owner },
            )
        assertTrue(runCatching { playback.stopDurably() }.exceptionOrNull() is IOException)
        allowCommit.set(true)
        val replacement = track("same-controller-fresh-track", 20.seconds)
        playback.play(listOf(replacement))
        playback.releaseForServiceShutdown()
        PlaybackPersistenceWriter.shared.flush()
        val saved = JSONObject(requireNotNull(preferences.getString(playbackPreferenceKey(owner), null)))
        assertEquals(replacement.id.value, saved.getJSONArray("original").getJSONObject(0).getString("id"))
        assertEquals(replacement.id, playback.state.value.current?.id)
    }

    private fun restoredQueue(owner: String): SharedPreferences {
        PlaybackPersistenceWriter.shared.flush()
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val song = track("durable-stop-track", 20.seconds)
        val saved = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray(listOf(song.toJson()))).put("order", JSONArray(listOf(0)))
            .put("index", 0).put("position", 4000).put("shuffle", false).put("repeat", "Off")
        check(preferences.edit().clear().putString(playbackPreferenceKey(owner), saved.toString()).commit())
        return preferences
    }

    private fun wrapped(
        preferences: SharedPreferences,
        stoppedThread: AtomicReference<Thread?>,
        commit: () -> Boolean,
    ): Context {
        val shared = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean = if (commit()) editor.commit() else false
                }
            }
        }
        return object : ContextWrapper(context) {
            override fun stopService(service: Intent): Boolean {
                stoppedThread.set(Thread.currentThread())
                return false
            }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "playback") shared else super.getSharedPreferences(name, mode)
        }
    }
}
