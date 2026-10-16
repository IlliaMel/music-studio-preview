package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackRestoreConcurrencyTest {
    @get:Rule val main = MainDispatcherRule()
    private val playbackScopes = PlaybackTestScopes(main.testDispatcher.scheduler)

    @After
    fun closePlaybackScopes() {
        playbackScopes.close()
    }
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun restoreDoesNotWaitForTheDiskCommitSerializationLock() = runTest {
        val owner = "restore-independent-io-lock"
        savedQueue(owner)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = playbackScopes.wrap(backgroundScope)
        val holder = Executors.newSingleThreadExecutor()
        val caller = Executors.newSingleThreadExecutor()
        try {
            holder.submit {
                synchronized(playbackPersistenceLock) {
                    held.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(held.await(2, TimeUnit.SECONDS))
            val restored = caller.submit<AndroidPlaybackController> {
                AndroidPlaybackController(context, scope, ownerId = { owner })
            }.get(2, TimeUnit.SECONDS)
            assertEquals("restore-track", restored.state.value.current?.id?.value)
            assertNotNull(restored.systemSnapshot())
        } finally {
            release.countDown()
            holder.shutdownNow()
            caller.shutdownNow()
        }
    }

    @Test
    fun failedPurgeCannotRestoreOldBytesUntilSuccessfulRetryAllowsFreshClaim() = runTest {
        val owner = "failed-purge-blocked-owner"
        val preferences = savedQueue(owner)
        val failing = preferencesContext(object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun remove(key: String?): SharedPreferences.Editor {
                        editor.remove(key)
                        return this
                    }
                    override fun commit(): Boolean = false
                }
            }
        })
        assertTrue(runCatching { clearPlaybackState(failing, owner) }.exceptionOrNull() is IOException)
        assertTrue(preferences.contains(playbackPreferenceKey(owner)))
        val blocked = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        assertNull(blocked.state.value.current)
        blocked.play(listOf(track("blocked-new-play", 20.seconds)))
        assertNull(blocked.state.value.current)
        assertNull(playbackRestoreGeneration(owner))
        clearPlaybackState(context, owner)
        assertFalse(preferences.contains(playbackPreferenceKey(owner)))
        assertNotNull(playbackRestoreGeneration(owner))
        savedQueue(owner)
        val fresh = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        assertEquals("restore-track", fresh.state.value.current?.id?.value)
        assertNotNull(fresh.systemSnapshot())
    }

    @Test
    fun purgeBetweenReadAndPublicationRejectsTheCapturedOldSnapshot() = runTest {
        val owner = "purge-during-restore-read"
        val preferences = savedQueue(owner)
        val intercepted = AtomicBoolean()
        val wrapped = preferencesContext(object : SharedPreferences by preferences {
            override fun getString(key: String?, defValue: String?): String? {
                val value = preferences.getString(key, defValue)
                if (key == playbackPreferenceKey(owner) && intercepted.compareAndSet(false, true)) {
                    clearPlaybackState(context, owner)
                }
                return value
            }
        })
        val restored = AndroidPlaybackController(wrapped, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        assertTrue(intercepted.get())
        assertNull(restored.state.value.current)
        assertNull(restored.systemSnapshot())
    }

    @Test
    fun restoreRetriesChangedAcceptedSnapshotAndTransfersTheLatestOverlayAtomically() = runTest {
        val owner = "restore-latest-accepted-snapshot"
        val preferences = savedQueue(owner)
        val key = playbackPreferenceKey(owner)
        val first = requireNotNull(preferences.getString(key, null))
        val latest = JSONObject(first).put("position", 12000).toString()
        val intercepted = AtomicBoolean()
        lateinit var old: PlaybackStatePersistence
        val shared = object : SharedPreferences by preferences {
            override fun getString(name: String?, defValue: String?): String? {
                val value = preferences.getString(name, defValue)
                if (name == key && intercepted.compareAndSet(false, true)) {
                    old.write(key, latest, owner, playbackStateGeneration(owner)) { true }
                }
                return value
            }
        }
        old = PlaybackStatePersistence(shared, { owner })
        old.claim(owner)
        val scope = playbackScopes.wrap(backgroundScope)
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holder = Executors.newSingleThreadExecutor()
        val caller = Executors.newSingleThreadExecutor()
        try {
            holder.submit {
                synchronized(playbackPersistenceLock) {
                    held.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(held.await(2, TimeUnit.SECONDS))
            val restored = caller.submit<AndroidPlaybackController> {
                AndroidPlaybackController(preferencesContext(shared), scope, ownerId = { owner })
            }.get(2, TimeUnit.SECONDS)
            assertTrue(intercepted.get())
            assertEquals(12000L, restored.state.value.positionMs)
            assertEquals(first, preferences.getString(key, null))
            old.write(key, first, owner, playbackStateGeneration(owner)) { true }
            val observer = PlaybackStatePersistence(shared, { owner })
            assertEquals(12000L, JSONObject(observer.readValue(key) as String).getLong("position"))
        } finally {
            release.countDown()
            holder.shutdownNow()
            caller.shutdownNow()
        }
        PlaybackPersistenceWriter.shared.flush()
        assertEquals(12000L, JSONObject(requireNotNull(preferences.getString(key, null))).getLong("position"))
    }

    private fun savedQueue(owner: String): SharedPreferences {
        PlaybackPersistenceWriter.shared.flush()
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val song = track("restore-track", 20.seconds)
        val json = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray().put(song.toJson())).put("order", JSONArray().put(0))
            .put("index", 0).put("position", 4000)
        check(preferences.edit().clear().putString(playbackPreferenceKey(owner), json.toString()).commit())
        return preferences
    }

    private fun preferencesContext(preferences: SharedPreferences): Context = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            if (name == "playback") preferences else super.getSharedPreferences(name, mode)
    }
}
