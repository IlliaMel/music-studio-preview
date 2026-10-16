package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.playback.PlaybackMetadataSnapshot
import com.illiamelnyk.studio.core.domain.playback.PlaybackTrackMetadata
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class PlaybackMetadataRefreshTest {
    @get:Rule val main = MainDispatcherRule()
    private val playbackScopes = PlaybackTestScopes(main.testDispatcher.scheduler)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val players = mutableListOf<MediaPlayer>()

    @After
    fun closePlaybackScopes() {
        players.forEach { it.release() }
        playbackScopes.close()
    }

    @Test
    fun pausedMetadataRefreshPreservesAudioClockModesAndUpdatesMediaSessionAndDisk() = runTest {
        val owner = "metadata-paused"
        val preferences = savedQueue(owner)
        val latest = MutableStateFlow<PlaybackMetadataSnapshot?>(null)
        val controller = AndroidPlaybackController(
            context,
            playbackScopes.wrap(backgroundScope),
            { owner },
            metadata = latest,
            isMetadataCurrent = { it == latest.value },
        )
        val before = controller.state.value
        val player = MediaPlayer().also { players += it }
        ReflectionHelpers.setField(controller, "player", player)
        ReflectionHelpers.setField(controller, "loadedId", before.current?.id?.value)
        val original = requireNotNull(before.current)
        latest.value = PlaybackMetadataSnapshot(
            owner,
            listOf(PlaybackTrackMetadata(original.id, "Renamed", "Updated artist", CoverSeed("updated-cover"))),
        )
        runCurrent()
        val after = controller.state.value
        assertEquals(
            before.copy(
                queue = before.queue.map {
                    if (it.id == original.id) {
                        it.copy(
                            title = "Renamed",
                            artist = "Updated artist",
                            coverSeed = CoverSeed("updated-cover"),
                        )
                    } else {
                        it
                    }
                },
            ),
            after,
        )
        assertFalse(after.isPlaying)
        assertSame(player, ReflectionHelpers.getField<Any?>(controller, "player"))
        assertEquals(original.id.value, ReflectionHelpers.getField<String>(controller, "loadedId"))
        assertEquals("Renamed", mediaMetadata(after, null).getString(MediaMetadata.METADATA_KEY_TITLE))
        PlaybackPersistenceWriter.shared.flush()
        assertEquals(
            4000L,
            JSONObject(requireNotNull(preferences.getString(playbackPreferenceKey(owner), null)))
                .getLong("position"),
        )
        val restored = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), { owner })
        assertEquals("Renamed", restored.state.value.current?.title)
        assertEquals(4000L, restored.state.value.positionMs)
        assertEquals(original.audioUri, restored.state.value.current?.audioUri)
    }

    @Test
    fun metadataRefreshUpdatesBothShuffledQueueAndOriginalOrderWithoutChangingIndex() = runTest {
        val owner = "metadata-shuffled"
        savedQueue(owner)
        val controller = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), { owner })
        controller.setShuffle(true)
        val before = controller.state.value
        val renamed = before.queue.map { PlaybackTrackMetadata(it.id, "New " + it.title, it.artist, it.coverSeed) }
        controller.refreshMetadata(PlaybackMetadataSnapshot(owner, renamed))
        assertEquals(before.currentIndex, controller.state.value.currentIndex)
        assertEquals(before.queue.map { it.id }, controller.state.value.queue.map { it.id })
        assertEquals(before.positionMs, controller.state.value.positionMs)
        controller.setShuffle(false)
        assertEquals(
            listOf("New Title metadata-one", "New Title metadata-two"),
            controller.state.value.queue.map { it.title },
        )
        assertEquals(4000L, controller.state.value.positionMs)
    }

    @Test
    fun foreignOwnerAndStaleSameUuidMetadataCannotOverwriteCurrentQueue() = runTest {
        var owner = "metadata-owner-b"
        savedQueue(owner)
        val beforeTrack = track("metadata-one", 23.seconds)
        val current = PlaybackMetadataSnapshot(
            owner,
            listOf(PlaybackTrackMetadata(beforeTrack.id, "B current", beforeTrack.artist, beforeTrack.coverSeed)),
        )
        val controller = AndroidPlaybackController(
            context,
            playbackScopes.wrap(backgroundScope),
            { owner },
            isMetadataCurrent = { it == current },
        )
        controller.refreshMetadata(current)
        val before = controller.state.value
        controller.refreshMetadata(current.copy(ownerId = "metadata-owner-a"))
        controller.refreshMetadata(current.copy(tracks = current.tracks.map { it.copy(title = "B stale") }))
        assertEquals(before, controller.state.value)
        owner = "metadata-owner-c"
        controller.refreshMetadata(current)
        assertEquals(before, controller.state.value)
    }

    @Test
    fun metadataAlreadyObservedBeforeAQueueLoadStillCanonicalizesTheNewQueue() = runTest {
        val owner = "metadata-later-load"
        savedQueue(owner)
        val controller = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), { owner })
        val original = requireNotNull(controller.state.value.current)
        controller.refreshMetadata(
            PlaybackMetadataSnapshot(
                owner,
                listOf(PlaybackTrackMetadata(original.id, "Latest title", original.artist, original.coverSeed)),
            ),
        )
        controller.play(listOf(original))
        assertEquals("Latest title", controller.state.value.current?.title)
        assertEquals(original.audioUri, controller.state.value.current?.audioUri)
    }

    @Test
    fun purgedGenerationRejectsMetadataBeforeAsynchronousStopRuns() = runTest {
        val owner = "metadata-purged"
        savedQueue(owner)
        val controller = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), { owner })
        val before = controller.state.value
        val original = requireNotNull(before.current)
        clearPlaybackState(context, owner)
        controller.refreshMetadata(
            PlaybackMetadataSnapshot(
                owner,
                listOf(PlaybackTrackMetadata(original.id, "Must not revive", original.artist, original.coverSeed)),
            ),
        )
        assertEquals(before, controller.state.value)
    }

    private fun savedQueue(owner: String): SharedPreferences {
        PlaybackPersistenceWriter.shared.flush()
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        val first = track("metadata-one", 23.seconds).copy(
            audioUri = "https://example.test/private/audio",
            audioHeaders = mapOf("X-Fixture" to "keep"),
            customLyrics = "Keep words",
            prompt = "Keep source prompt",
            remixTrackIds = listOf("source-id"),
        )
        val second = track("metadata-two", 29.seconds)
        val json = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray().put(first.toJson()).put(second.toJson()))
            .put("order", JSONArray().put(0).put(1)).put("index", 0).put("position", 4000)
        check(preferences.edit().clear().putString(playbackPreferenceKey(owner), json.toString()).commit())
        return preferences
    }
}
