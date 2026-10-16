package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AudioPlaybackTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @org.junit.After
    fun clearPlaybackFixture() {
        PlaybackPersistenceWriter.shared.flush()
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun autoplayPreferenceAndHistoryRestorePausedAndArePurgedWithOwner() = runTest {
        val song = track("autoplay-native", 20.seconds)
        val next = track("autoplay-next", 20.seconds)
        saveQueue("autoplay-owner", listOf(song))
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "autoplay-owner" })
        playback.setAutoplay(true)
        playback.appendAutoplay(playback.state.value, next)
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { "autoplay-owner" })
        assertTrue(restored.state.value.autoplay)
        assertFalse(restored.state.value.isPlaying)
        assertEquals(listOf(song.id, next.id), restored.state.value.queue.map { it.id })
        assertTrue(next.id in restored.state.value.autoplayHistory)
        val other = AndroidPlaybackController(context, backgroundScope, ownerId = { "another-owner" })
        assertFalse(other.state.value.autoplay)
        assertTrue(other.state.value.autoplayHistory.isEmpty())
        clearPlaybackState(context, "autoplay-owner")
        val cleared = AndroidPlaybackController(context, backgroundScope, ownerId = { "autoplay-owner" })
        assertFalse(cleared.state.value.autoplay)
        assertFalse(
            context.getSharedPreferences(
                "playback",
                Context.MODE_PRIVATE,
            ).contains(autoplayPreferenceKey("autoplay-owner")),
        )
    }

    @Test
    fun staleOwnerCannotAppendAutoplayTrackOrPersistPreference() = runTest {
        var owner = "autoplay-original"
        val song = track("autoplay-owned", 20.seconds)
        saveQueue(owner, listOf(song))
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        playback.setAutoplay(true)
        val expected = playback.state.value
        owner = "autoplay-other"
        playback.appendAutoplay(expected, track("autoplay-rejected", 20.seconds))
        assertEquals(listOf(song.id), playback.state.value.queue.map { it.id })
        assertFalse(
            context.getSharedPreferences("playback", Context.MODE_PRIVATE).contains(autoplayPreferenceKey(owner)),
        )
    }

    @Test
    fun `local demo exports audible WAV with declared track duration`() = runTest {
        val song = track("audio-fixture", 5.seconds)
        val file = AudioFiles(context, ownerId = { "audio-test-owner" }, authorization = { emptyMap() }).export(song)
        val bytes = file.readBytes()
        assertEquals("wav", file.extension)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16000, buffer.getInt(24))
        assertEquals(16000 * 5 * 2, buffer.getInt(40))
        assertTrue(bytes.drop(44).any { it.toInt() != 0 })
        assertEquals(
            file,
            AudioFiles(context, ownerId = {
                "audio-test-owner"
            }, authorization = { emptyMap() }).export(song),
        )
    }

    @Test
    fun `export identifies imported MPEG4 audio instead of falsely labelling MP3`() = runTest {
        val source = java.io.File(context.filesDir, "reference-container.audio")
        val bytes = ByteArray(64)
        "ftyp".toByteArray().copyInto(bytes, destinationOffset = 4)
        source.writeBytes(bytes)
        val song = track("container-fixture", 5.seconds).copy(audioUri = android.net.Uri.fromFile(source).toString())
        val exported = AudioFiles(context, ownerId = {
            "audio-test-owner"
        }, authorization = { emptyMap() }).export(song)
        assertEquals("m4a", exported.extension)
        org.junit.Assert.assertArrayEquals(bytes, exported.readBytes())
    }

    @Test
    fun collidingJavaHashesCannotReuseOrDeleteAnotherTracksAudio() = runTest {
        val ids = listOf(
            "77cbc1e5-b179-4e69-823f-25e10268b6ae",
            "b3d03c3c-cf73-47a6-ae51-38a2bae517fc",
        )
        assertEquals(ids[0].hashCode(), ids[1].hashCode())
        val songs = ids.mapIndexed { index, id ->
            val source = java.io.File(context.filesDir, "collision-$index.m4a")
            val bytes = ByteArray(64) { (index + 1).toByte() }
            "ftyp".toByteArray().copyInto(bytes, destinationOffset = 4)
            source.writeBytes(bytes)
            track(id, 5.seconds).copy(audioUri = android.net.Uri.fromFile(source).toString())
        }
        val first = AudioFiles(context, ownerId = {
            "audio-test-owner"
        }, authorization = { emptyMap() }).export(songs[0])
        val second = AudioFiles(context, ownerId = {
            "audio-test-owner"
        }, authorization = { emptyMap() }).export(songs[1])
        assertFalse(first == second)
        assertEquals(1, first.readBytes().last().toInt())
        assertEquals(2, second.readBytes().last().toInt())
        com.illiamelnyk.studio.core.data.local.RecordingFiles(context).deleteCached(songs[0].id, "audio-test-owner")
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(
            second,
            AudioFiles(context, ownerId = {
                "audio-test-owner"
            }, authorization = { emptyMap() }).export(songs[1]),
        )
        val demos = songs.map {
            AudioFiles(context, ownerId = {
                "audio-test-owner"
            }, authorization = { emptyMap() }).export(it.copy(audioUri = null))
        }
        assertFalse(demos[0] == demos[1])
        com.illiamelnyk.studio.core.data.local.RecordingFiles(context).deleteCached(songs[0].id, "audio-test-owner")
        assertFalse(demos[0].exists())
        assertTrue(demos[1].exists())
    }

    @Test
    fun `custom lyrics survive and are timed across the track`() = runTest {
        val song = track("custom-audio", 20.seconds).copy(customLyrics = "First line\nSecond line")
        val tracks = com.illiamelnyk.studio.feature.player.fake.InMemoryTrackRepository(mine = listOf(song))
        val outcome = LocalLyricsRepository(tracks).lyrics(song.id)
        val lines = (outcome as com.illiamelnyk.studio.core.domain.Outcome.Success).value
        assertEquals(listOf("First line", "Second line"), lines.map { it.text })
        assertEquals(10000L, lines[1].startMs)
        assertEquals(20000L, lines.last().endMs)
    }

    @Test
    fun `saved playback restores queue position and pauses instead of autoplaying`() = runTest {
        val song = track("restore-audio", 20.seconds)
        saveQueue("owner-a", listOf(song), position = 4250, shuffle = true, repeat = "All")
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        val restored = playback.state.value
        assertNotNull(restored.current)
        assertEquals(song.id, restored.current?.id)
        assertEquals(4250L, restored.positionMs)
        assertTrue(restored.shuffle)
        assertFalse(restored.isPlaying)
        assertEquals(com.illiamelnyk.studio.core.domain.playback.RepeatMode.All, restored.repeat)
    }

    @Test
    fun noActiveAccountAndMismatchedOwnerNeverRestorePrivateQueue() = runTest {
        val song = track("private-audio", 20.seconds).copy(audioHeaders = mapOf("Authorization" to "Bearer fixture"))
        saveQueue("owner-a", listOf(song))
        val signedOut = AndroidPlaybackController(context, backgroundScope, ownerId = { null })
        assertTrue(signedOut.state.value.queue.isEmpty())
        val otherOwner = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-b" })
        assertTrue(otherOwner.state.value.queue.isEmpty())
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val privateState = preferences.getString(playbackPreferenceKey("owner-a"), null)
        preferences.edit().putString(playbackPreferenceKey("owner-b"), privateState).commit()
        val mismatched = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-b" })
        assertTrue(mismatched.state.value.queue.isEmpty())
        assertFalse(preferences.contains(playbackPreferenceKey("owner-b")))
    }

    @Test
    fun legacyUnownedStateIsDurablyDiscarded() = runTest {
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val legacy = JSONObject().put("queue", JSONArray().put(track("legacy", 20.seconds).toJson()))
        preferences.edit().putString("state", legacy.toString()).commit()
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        assertTrue(playback.state.value.queue.isEmpty())
        assertFalse(preferences.contains("state"))
    }

    @Test
    fun stopClearsBeforeSessionChangeAndDelayedCollectorCannotResurrectQueue() = runTest {
        var owner: String? = "owner-a"
        saveQueue("owner-a", listOf(track("private-audio", 20.seconds)), position = 4250)
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        playback.seekTo(8500)
        playback.stop()
        owner = null
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        assertFalse(preferences.contains(playbackPreferenceKey("owner-a")))
        val signedOut = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        assertTrue(signedOut.state.value.queue.isEmpty())
        runCurrent()
        assertFalse(preferences.contains(playbackPreferenceKey("owner-a")))
        owner = "owner-b"
        val other = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        assertTrue(other.state.value.queue.isEmpty())
        assertFalse(preferences.contains(playbackPreferenceKey("owner-b")))
    }

    @Test
    fun delayedOldOwnerWritesCannotBeRelabelledAsTheNewOwner() = runTest {
        var owner: String? = "owner-a"
        saveQueue("owner-a", listOf(track("private-audio", 20.seconds)), position = 4250)
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        playback.seekTo(9000)
        owner = "owner-b"
        runCurrent()
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        assertFalse(preferences.contains(playbackPreferenceKey("owner-b")))
        val saved = JSONObject(requireNotNull(preferences.getString(playbackPreferenceKey("owner-a"), null)))
        assertEquals(4250L, saved.getLong("position"))
    }

    @Test
    fun accountPurgeInvalidatesDelayedPausedWriterEvenWhenAccountIdIsReused() = runTest {
        var owner: String? = "local-demo"
        saveQueue("local-demo", listOf(track("private-draft", 20.seconds)), position = 4250)
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        playback.seekTo(9000)
        owner = null
        clearPlaybackState(context, "local-demo")
        owner = "local-demo"
        runCurrent()
        assertTrue(playback.state.value.queue.isEmpty())
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        assertFalse(preferences.contains(playbackPreferenceKey("local-demo")))
        val recreated = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        assertTrue(recreated.state.value.queue.isEmpty())
    }

    @Test
    fun shuffledRestartRetainsOriginalOrderForTurningShuffleOff() = runTest {
        val songs = listOf("a", "b", "c", "d").map { track(it, 20.seconds) }
        saveQueue("owner-a", songs, index = 2)
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        playback.setShuffle(true)
        val shuffled = playback.state.value.queue.map { it.id }
        runCurrent()
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        assertEquals(shuffled, restored.state.value.queue.map { it.id })
        assertEquals(songs[2].id, restored.state.value.current?.id)
        restored.setShuffle(false)
        assertEquals(songs.map { it.id }, restored.state.value.queue.map { it.id })
        assertEquals(2, restored.state.value.currentIndex)
    }

    @Test
    fun removingUpcomingTrackPreservesCurrentClockAndPersistsRemovalImmediately() = runTest {
        val songs = listOf("a", "b", "c").map { track(it, 20.seconds) }
        saveQueue("owner-a", songs, position = 4250)
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        playback.removeTrack(songs[1].id)
        assertEquals(listOf(songs[0].id, songs[2].id), playback.state.value.queue.map { it.id })
        assertEquals(4250L, playback.state.value.positionMs)
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        assertEquals(playback.state.value.queue.map { it.id }, restored.state.value.queue.map { it.id })
        assertEquals(songs[0].id, restored.state.value.current?.id)
    }

    @Test
    fun removingCurrentTrackSelectsNextAndRemovingLastTrackDurablyStops() = runTest {
        val songs = listOf("a", "b").map { track(it, 20.seconds) }
        saveQueue("owner-a", songs, position = 4250)
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        playback.removeTrack(songs[0].id)
        assertEquals(songs[1].id, playback.state.value.current?.id)
        assertEquals(0L, playback.state.value.positionMs)
        assertFalse(playback.state.value.isPlaying)
        playback.removeTrack(songs[1].id)
        assertTrue(playback.state.value.queue.isEmpty())
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        assertFalse(preferences.contains(playbackPreferenceKey("owner-a")))
        runCurrent()
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        assertTrue(restored.state.value.queue.isEmpty())
    }

    @Test
    fun staleOtherOwnerStopDoesNotDeleteCurrentOwnersRestoredQueue() = runTest {
        var owner = "stale-stop-a"
        val first = track("old-owner-track", 20.seconds)
        val second = track("new-owner-track", 20.seconds)
        saveQueue(owner, listOf(first))
        val stale = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        owner = "stale-stop-b"
        saveQueue(owner, listOf(second), position = 6250)
        val replacement = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        replacement.setAutoplay(true)
        stale.stop()
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        assertEquals(second.id, restored.state.value.current?.id)
        assertTrue(restored.state.value.autoplay)
        assertFalse(restored.state.value.isPlaying)
    }

    @Test
    fun staleSameOwnerControllerStopAndWriterCannotEraseReplacementQueue() = runTest {
        val owner = "same-owner-replacement"
        val first = track("old-controller-track", 20.seconds)
        val second = track("new-controller-track", 20.seconds)
        saveQueue(owner, listOf(first))
        val stale = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        saveQueue(owner, listOf(second), position = 6250)
        val replacement = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        replacement.setAutoplay(true)
        stale.setAutoplay(false)
        stale.stop()
        runCurrent()
        PlaybackPersistenceWriter.shared.flush()
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        assertEquals(second.id, restored.state.value.current?.id)
        assertTrue(restored.state.value.autoplay)
        assertEquals(6000L, restored.state.value.positionMs)
        assertFalse(restored.state.value.isPlaying)
    }

    @Test
    fun shutdownFlushReleasesPlaybackLockBeforeWaitingForCheckedDiskCommit() = runTest {
        val song = track("flush-lock", 20.seconds)
        saveQueue("flush-lock-owner", listOf(song), position = 4250)
        val original = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        var playbackLock: Any? = null
        val committed = java.util.concurrent.CountDownLatch(1)
        val wrapped = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                if (name != "playback") return super.getSharedPreferences(name, mode)
                return object : android.content.SharedPreferences by original {
                    override fun edit(): android.content.SharedPreferences.Editor {
                        val editor = original.edit()
                        return object : android.content.SharedPreferences.Editor by editor {
                            override fun commit(): Boolean {
                                synchronized(requireNotNull(playbackLock)) {
                                    committed.countDown()
                                    return editor.commit()
                                }
                            }
                        }
                    }
                }
            }
        }
        val playback = AndroidPlaybackController(wrapped, backgroundScope, ownerId = { "flush-lock-owner" })
        playbackLock = AndroidPlaybackController::class.java.getDeclaredField("playbackLock").apply {
            isAccessible = true
        }.get(playback)
        playback.seekTo(8500)
        val caller = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            caller.submit { playback.releaseForServiceShutdown() }.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(committed.await(1, java.util.concurrent.TimeUnit.SECONDS))
            val saved = JSONObject(requireNotNull(original.getString(playbackPreferenceKey("flush-lock-owner"), null)))
            assertEquals(8000L, saved.getLong("position"))
        } finally {
            caller.shutdownNow()
        }
    }

    @Test
    fun failedRemoteAudioNeverCreatesSyntheticFallback() = runTest {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/audio") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        val url = "http://127.0.0.1:" + server.address.port + "/audio"
        val song = track("failed-remote", 5.seconds).copy(audioUri = url)
        val demo = java.io.File(context.cacheDir, "audio/demo-" + audioCacheKey(song.id) + ".wav")
        demo.delete()
        try {
            assertEquals(song.audioUri, AudioFiles(context).playableUri(song).toString())
            assertTrue(
                runCatching {
                    AudioFiles(context, ownerId = { "audio-test-owner" }, authorization = { emptyMap() }).export(song)
                }.isFailure,
            )
            assertFalse(demo.exists())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun callbacksFromPreviousNativeTrackCannotAlterReplacementOrStoppedQueue() = runTest {
        val created = mutableListOf<org.robolectric.shadows.ShadowMediaPlayer>()
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(20_000, 10_000)
        }
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { _, shadow -> created += shadow }
        val songs = listOf("a", "b", "c").map {
            track(it, 20.seconds).copy(audioUri = "https://audio.fixture/" + it + ".wav")
        }
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        try {
            playback.play(songs)
            runCurrent()
            assertEquals(1, created.size)
            val previous = created.single()
            playback.play(songs.drop(1))
            val replacement = playback.state.value
            previous.invokePreparedListener()
            previous.invokeCompletionListener()
            previous.invokeErrorListener(1, 0)
            assertEquals(replacement, playback.state.value)
            assertFalse(previous.isReallyPlaying)
            playback.stop()
            previous.invokeCompletionListener()
            previous.invokePreparedListener()
            previous.invokeErrorListener(1, 0)
            assertTrue(playback.state.value.queue.isEmpty())
        } finally {
            playback.stop()
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }

    @Test
    fun backgroundOwnerLossCannotStartPreparedAudioAndStopsLiveQueue() = runTest {
        var owner: String? = "owner-a"
        val created = mutableListOf<org.robolectric.shadows.ShadowMediaPlayer>()
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(20_000, 10_000)
        }
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { _, shadow -> created += shadow }
        val song = track("owner-audio", 20.seconds).copy(audioUri = "https://audio.fixture/owner.wav")
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        try {
            playback.play(listOf(song))
            runCurrent()
            val native = created.single()
            owner = null
            native.invokePreparedListener()
            assertFalse(native.isReallyPlaying)
            advanceTimeBy(251)
            runCurrent()
            assertTrue(playback.state.value.queue.isEmpty())
            val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
            assertFalse(preferences.contains(playbackPreferenceKey("owner-a")))
        } finally {
            playback.stop()
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }

    @Test
    fun systemResumeRequiresExplicitPlayAndMatchingCurrentOwnerMediaId() = runTest {
        var owner: String? = "owner-a"
        val song = track("public-catalog-id", 20.seconds)
        saveQueue("owner-a", listOf(song), position = 4_250)
        saveQueue("owner-b", listOf(song), position = 7_250)
        val first = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        val firstId = requireNotNull(first.systemSnapshot()).mediaId
        assertFalse(first.state.value.isPlaying)
        owner = "owner-b"
        assertNull(first.systemSnapshot())
        val replacement = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        val secondId = requireNotNull(replacement.systemSnapshot()).mediaId
        assertFalse(firstId == secondId)
        assertFalse(resumeSystemPlayback(replacement, firstId))
        assertFalse(replacement.state.value.isPlaying)
        assertTrue(resumeSystemPlayback(replacement, secondId))
        assertTrue(replacement.state.value.isPlaying)
        replacement.releaseForServiceShutdown()
        assertFalse(replacement.state.value.isPlaying)
        val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { owner })
        assertEquals(secondId, restored.systemSnapshot()?.mediaId)
        assertEquals(7_000L, restored.state.value.positionMs)
        assertFalse(restored.state.value.isPlaying)
        owner = null
        assertNull(restored.systemSnapshot())
        assertFalse(resumeSystemPlayback(restored, secondId))
        replacement.stop()
    }

    @Test
    fun serviceShutdownReleasesNativeAudioAndKeepsOwnerScopedPausedResumeQueue() = runTest {
        val created = mutableListOf<org.robolectric.shadows.ShadowMediaPlayer>()
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(20_000, 10_000)
        }
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { _, shadow -> created += shadow }
        val song = track("shutdown-audio", 20.seconds).copy(audioUri = "https://audio.fixture/shutdown.wav")
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        try {
            playback.play(listOf(song))
            runCurrent()
            val native = created.single()
            native.invokePreparedListener()
            assertTrue(native.isReallyPlaying)
            playback.seekTo(8_500)
            playback.releaseForServiceShutdown()
            assertFalse(native.isReallyPlaying)
            assertFalse(playback.state.value.isPlaying)
            assertEquals(song.id, playback.state.value.current?.id)
            val restored = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
            assertEquals(song.id, restored.state.value.current?.id)
            assertEquals(8_000L, restored.state.value.positionMs)
            assertFalse(restored.state.value.isPlaying)
            val stable = playback.state.value
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(stable, playback.state.value)
        } finally {
            playback.stop()
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }

    @Test
    fun ownerChangeDuringHeaderRefreshCannotPrepareOrStartPrivateAudio() = runTest {
        var owner: String? = "owner-a"
        val freshHeaders = kotlinx.coroutines.CompletableDeferred<Map<String, String>>()
        var headersRequested = false
        val created = mutableListOf<org.robolectric.shadows.ShadowMediaPlayer>()
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { _, shadow -> created += shadow }
        val playback = AndroidPlaybackController(
            context,
            backgroundScope,
            ownerId = { owner },
            audioHeaders = {
                headersRequested = true
                freshHeaders.await()
            },
        )
        try {
            val song = track("private-refresh", 20.seconds).copy(audioUri = "https://audio.fixture/private.wav")
            playback.play(listOf(song))
            runCurrent()
            assertTrue(headersRequested)
            assertTrue(created.isEmpty())
            owner = "owner-b"
            freshHeaders.complete(mapOf("Authorization" to "Bearer fresh-fixture"))
            runCurrent()
            assertTrue(created.isEmpty())
            assertNull(playback.systemSnapshot())
            advanceTimeBy(251)
            runCurrent()
            assertTrue(playback.state.value.queue.isEmpty())
        } finally {
            playback.stop()
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }

    @Test
    fun nativeFailuresSurfaceTypedCauseOnceAndPlayRetriesWithoutStaleCallbackErrors() = runTest {
        val created = mutableListOf<org.robolectric.shadows.ShadowMediaPlayer>()
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider {
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(20_000, 10_000)
        }
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { _, shadow -> created += shadow }
        val song = track("failure-audio", 20.seconds).copy(audioUri = "https://audio.fixture/failure.wav")
        val playback = AndroidPlaybackController(context, backgroundScope, ownerId = { "owner-a" })
        try {
            playback.play(listOf(song))
            runCurrent()
            val native = created.single()
            native.invokePreparedListener()
            playback.togglePlayPause()
            assertNull(playback.state.value.failure)
            playback.togglePlayPause()
            native.invokeErrorListener(1, android.media.MediaPlayer.MEDIA_ERROR_IO)
            val first = playback.state.value.failure!!
            assertEquals(com.illiamelnyk.studio.core.domain.AppError.Network, first.error)
            assertEquals(song.id, first.trackId)
            assertFalse(playback.state.value.isPlaying)
            assertFalse(playback.state.value.autoplayWaiting)
            playback.togglePlayPause()
            assertNull(playback.state.value.failure)
            advanceTimeBy(251)
            runCurrent()
            val replacement = created.last()
            assertEquals(2, created.size)
            native.invokeErrorListener(1, android.media.MediaPlayer.MEDIA_ERROR_IO)
            assertNull(playback.state.value.failure)
            replacement.invokeErrorListener(1, android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT)
            val second = playback.state.value.failure!!
            assertEquals(com.illiamelnyk.studio.core.domain.AppError.Timeout, second.error)
            assertTrue(second.id > first.id)
            playback.stop()
            assertNull(playback.state.value.failure)
        } finally {
            playback.stop()
            org.robolectric.shadows.ShadowMediaPlayer.resetStaticState()
        }
    }

    @Test
    fun typedHeaderFailureSurfacesButOwnerChangeDuringFailureDoesNotLeakFeedback() = runTest {
        val song = track("header-failure", 20.seconds).copy(audioUri = "https://audio.fixture/private.wav")
        val playback = AndroidPlaybackController(
            context,
            backgroundScope,
            ownerId = { "owner-a" },
            audioHeaders = {
                throw com.illiamelnyk.studio.core.data.local.ApiFailure(
                    com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
                )
            },
        )
        playback.play(listOf(song))
        runCurrent()
        assertEquals(com.illiamelnyk.studio.core.domain.AppError.Unauthorized, playback.state.value.failure?.error)
        playback.stop()
        var owner = "owner-a"
        val pending = kotlinx.coroutines.CompletableDeferred<Map<String, String>>()
        val changed =
            AndroidPlaybackController(context, backgroundScope, ownerId = { owner }, audioHeaders = { pending.await() })
        try {
            changed.play(listOf(song))
            runCurrent()
            owner = "owner-b"
            pending.completeExceptionally(
                com.illiamelnyk.studio.core.data.local.ApiFailure(
                    com.illiamelnyk.studio.core.domain.AppError.Unauthorized,
                ),
            )
            runCurrent()
            assertNull(changed.state.value.failure)
        } finally {
            changed.stop()
        }
    }

    private fun saveQueue(
        owner: String,
        songs: List<com.illiamelnyk.studio.core.domain.track.Track>,
        position: Long = 0,
        index: Int = 0,
        shuffle: Boolean = false,
        repeat: String = "Off",
    ) {
        val saved = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray(songs.map { it.toJson() })).put("order", JSONArray(songs.indices.toList()))
            .put("index", index).put("position", position).put("shuffle", shuffle).put("repeat", repeat)
        context.getSharedPreferences("playback", Context.MODE_PRIVATE)
            .edit().putString(playbackPreferenceKey(owner), saved.toString()).commit()
    }
}
