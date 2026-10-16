package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.feature.player.fake.track
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackStatePersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun acceptedSnapshotIsVisibleAcrossAdaptersAndConflatesBeforeSerialIo() {
        withBlockedWriter { writer, release ->
            val original = preferences("conflated")
            val commits = AtomicInteger()
            val caller = Thread.currentThread()
            val preferences = checkingPreferences(original) {
                assertFalse(Thread.currentThread() === caller)
                commits.incrementAndGet()
                true
            }
            val first = PlaybackStatePersistence(preferences, { "owner" }, writer)
            first.claim("owner")
            val second = PlaybackStatePersistence(preferences, { "owner" }, writer)
            repeat(100) { first.write("queue", "snapshot-$it", "owner", playbackStateGeneration("owner")) { true } }
            assertNull(original.getString("queue", null))
            assertEquals("snapshot-99", second.read("queue"))
            release.countDown()
            first.flush()
            assertEquals("snapshot-99", original.getString("queue", null))
            assertEquals(1, commits.get())
        }
    }

    @Test
    fun queuedOldOwnerAndPurgedGenerationCannotWriteEvenWhenOwnerIdIsReused() {
        withBlockedWriter { writer, release ->
            var owner: String? = "purged-writer-owner"
            val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
            val key = playbackPreferenceKey(requireNotNull(owner))
            val generation = playbackStateGeneration(requireNotNull(owner))
            val persistence = PlaybackStatePersistence(preferences, { owner }, writer)
            persistence.claim(requireNotNull(owner))
            persistence.write(key, "private", requireNotNull(owner), generation) { true }
            owner = null
            clearPlaybackState(context, "purged-writer-owner")
            owner = "purged-writer-owner"
            assertNull(persistence.read(key))
            release.countDown()
            persistence.flush()
            assertFalse(preferences.contains(key))
        }
        withBlockedWriter { writer, release ->
            var owner = "old-owner"
            val preferences = preferences("owner-switch")
            val persistence = PlaybackStatePersistence(preferences, { owner }, writer)
            persistence.write("queue", "private", owner, playbackStateGeneration(owner)) { true }
            owner = "new-owner"
            release.countDown()
            persistence.flush()
            assertFalse(preferences.contains("queue"))
        }
    }

    @Test
    fun invalidatedEpochAndQueuedRemovalCannotResurrectStoppedQueue() {
        withBlockedWriter { writer, release ->
            val preferences = preferences("stopped")
            preferences.edit().putString("queue", "old").commit()
            val persistence = PlaybackStatePersistence(preferences, { "owner" }, writer)
            persistence.claim("owner")
            val epoch = AtomicLong()
            val captured = epoch.get()
            persistence.write("queue", "late", "owner", playbackStateGeneration("owner")) { epoch.get() == captured }
            epoch.incrementAndGet()
            persistence.remove(listOf("queue"))
            assertNull(persistence.read("queue"))
            release.countDown()
            persistence.flush()
            assertFalse(preferences.contains("queue"))
        }
    }

    @Test
    fun staleLeaseCannotDisplaceQueuedReplacementOrDeleteItsQueue() {
        withBlockedWriter { writer, release ->
            val preferences = preferences("replacement")
            val old = PlaybackStatePersistence(preferences, { "owner" }, writer)
            val replacement = PlaybackStatePersistence(preferences, { "owner" }, writer)
            val generation = playbackStateGeneration("owner")
            old.claim("owner")
            old.write(playbackPreferenceKey("owner"), "old", "owner", generation) { true }
            replacement.claim("owner")
            replacement.write(playbackPreferenceKey("owner"), "new", "owner", generation) { true }
            old.removeQueue("owner", generation)
            old.write(playbackPreferenceKey("owner"), "late-old", "owner", generation) { true }
            assertEquals("new", replacement.read(playbackPreferenceKey("owner")))
            release.countDown()
            replacement.flush()
            assertEquals("new", preferences.getString(playbackPreferenceKey("owner"), null))
        }
    }

    @Test
    fun invalidRestoreCleanupCannotDisplaceClaimedQueuedReplacement() {
        withBlockedWriter { writer, release ->
            val owner = "invalid-restore-owner"
            val key = playbackPreferenceKey(owner)
            val preferences = preferences("invalid-restore")
            preferences.edit().putString(key, "{invalid").commit()
            val failedRestore = PlaybackStatePersistence(preferences, { owner }, writer)
            val observed = failedRestore.readValue(key)
            val generation = playbackStateGeneration(owner)
            val replacement = PlaybackStatePersistence(preferences, { owner }, writer)
            val valid = JSONObject().apply {
                put("version", 2)
                put("owner", owner)
                put("original", JSONArray().put(track("replacement", 20.seconds).toJson()))
                put("order", JSONArray().put(0))
                put("index", 0)
                put("position", 4000)
            }.toString()
            replacement.claim(owner)
            replacement.write(key, valid, owner, generation) { true }
            failedRestore.removeUnclaimed(owner, generation, observed)
            assertEquals(valid, replacement.read(key))
            release.countDown()
            failedRestore.flush()
            assertEquals(valid, preferences.getString(key, null))
        }
    }

    @Test
    fun invalidRestoreCleanupRechecksStoredValueBeforeCommit() {
        withBlockedWriter { writer, release ->
            val owner = "changed-invalid-restore-owner"
            val key = playbackPreferenceKey(owner)
            val preferences = preferences("invalid-restore-changed")
            preferences.edit().putBoolean(key, true).commit()
            val persistence = PlaybackStatePersistence(preferences, { owner }, writer)
            val observed = persistence.readValue(key)
            persistence.removeUnclaimed(owner, playbackStateGeneration(owner), observed)
            preferences.edit().putString(key, "replacement-from-new-store").commit()
            release.countDown()
            persistence.flush()
            assertEquals("replacement-from-new-store", preferences.getString(key, null))
        }
        withBlockedWriter { writer, release ->
            val owner = "unchanged-invalid-restore-owner"
            val key = playbackPreferenceKey(owner)
            val preferences = preferences("invalid-restore-unchanged")
            preferences.edit().putBoolean(key, true).commit()
            val persistence = PlaybackStatePersistence(preferences, { owner }, writer)
            persistence.removeUnclaimed(owner, playbackStateGeneration(owner), persistence.readValue(key))
            release.countDown()
            persistence.flush()
            assertFalse(preferences.contains(key))
        }
    }

    @Test
    fun checkedCommitFailureIsReportedAtDurabilityBoundary() {
        withBlockedWriter { writer, release ->
            val preferences = checkingPreferences(preferences("failed")) { false }
            val persistence = PlaybackStatePersistence(preferences, { "owner" }, writer)
            persistence.claim("owner")
            persistence.write("queue", "private", "owner", playbackStateGeneration("owner")) { true }
            release.countDown()
            val error = runCatching { persistence.flush() }.exceptionOrNull()
            assertTrue(error is IOException)
            assertNull(persistence.read("queue"))
        }
    }

    private fun preferences(name: String): SharedPreferences =
        context.getSharedPreferences("playback-writer-$name", Context.MODE_PRIVATE).apply { edit().clear().commit() }

    private fun checkingPreferences(preferences: SharedPreferences, commit: () -> Boolean): SharedPreferences =
        object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }

                    override fun remove(key: String): SharedPreferences.Editor {
                        editor.remove(key)
                        return this
                    }

                    override fun commit(): Boolean = if (commit()) editor.commit() else false
                }
            }
        }

    private fun withBlockedWriter(block: (PlaybackPersistenceWriter, CountDownLatch) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        executor.execute {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        check(started.await(5, TimeUnit.SECONDS))
        try {
            block(PlaybackPersistenceWriter(executor), release)
        } finally {
            release.countDown()
            executor.shutdown()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
