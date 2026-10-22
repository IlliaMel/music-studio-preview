package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.domain.playback.PlaybackMetadataSnapshot
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.core.domain.playback.PlaybackTrackMetadata
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
class PlaybackSnapshotCacheTest {
    @get:Rule val main = MainDispatcherRule()
    private val playbackScopes = PlaybackTestScopes(main.testDispatcher.scheduler)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun close() {
        playbackScopes.close()
    }

    @Test fun subsecondTicksReuseSerializationAndNextSecondUpdatesPosition() = runPlaybackTest {
        val controller = controller("snapshot-clock", backgroundScope) { "snapshot-clock" }
        val state = controller.state.value
        val first = requireNotNull(snapshot(controller, state.copy(positionMs = 4000)))
        for (position in listOf(4250L, 4500L, 4750L)) {
            assertSame(first, snapshot(controller, state.copy(positionMs = position)))
        }
        val next = requireNotNull(snapshot(controller, state.copy(positionMs = 5000)))
        assertNotSame(first, next)
        assertEquals(4000L, JSONObject(first.second).getLong("position"))
        assertEquals(5000L, JSONObject(next.second).getLong("position"))
    }

    @Test fun metadataAndSerializedControlsInvalidateCache() = runPlaybackTest {
        val owner = "snapshot-controls"
        val controller = controller(owner, backgroundScope) { owner }
        val before = requireNotNull(snapshot(controller, controller.state.value))
        val original = requireNotNull(controller.state.value.current)
        controller.refreshMetadata(
            PlaybackMetadataSnapshot(
                owner,
                listOf(
                    PlaybackTrackMetadata(original.id, "Renamed", "New artist", original.coverSeed),
                ),
            ),
        )
        val state = controller.state.value
        val renamed = requireNotNull(snapshot(controller, state))
        assertNotSame(before, renamed)
        assertEquals("Renamed", JSONObject(renamed.second).getJSONArray("original").getJSONObject(0).getString("title"))
        val variants = listOf(
            state.copy(currentIndex = 1),
            state.copy(positionMs = 7000),
            state.copy(shuffle = true),
            state.copy(repeat = RepeatMode.All),
            state.copy(stationName = "Station"),
            state.copy(autoplay = true),
            state.copy(autoplayHistory = linkedSetOf(TrackId("new-history"))),
        )
        for (changed in variants) {
            val baseline = requireNotNull(snapshot(controller, state))
            assertNotSame(baseline, snapshot(controller, changed))
        }
        val forward = state.copy(autoplayHistory = linkedSetOf(TrackId("a"), TrackId("b")))
        val reverse = state.copy(autoplayHistory = linkedSetOf(TrackId("b"), TrackId("a")))
        val firstOrder = requireNotNull(snapshot(controller, forward))
        val secondOrder = requireNotNull(snapshot(controller, reverse))
        assertNotSame(firstOrder, secondOrder)
        assertEquals("a", JSONObject(firstOrder.second).getJSONArray("autoplayHistory").getString(0))
        assertEquals("b", JSONObject(secondOrder.second).getJSONArray("autoplayHistory").getString(0))
    }

    @Test fun cachedSnapshotNeverBypassesOwnerOrGenerationValidation() = runPlaybackTest {
        val savedOwner = "snapshot-owner"
        var owner: String? = savedOwner
        val controller = controller(savedOwner, backgroundScope) { owner }
        val state = controller.state.value
        val valid = requireNotNull(snapshot(controller, state))
        owner = null
        assertNull(snapshot(controller, state))
        owner = "different-owner"
        assertNull(snapshot(controller, state))
        owner = savedOwner
        assertEquals(valid, snapshot(controller, state))
        clearPlaybackState(context, savedOwner)
        assertNull(snapshot(controller, state))
    }

    private fun controller(savedOwner: String, scope: CoroutineScope, owner: () -> String?): AndroidPlaybackController {
        PlaybackPersistenceWriter.shared.flush()
        val songs = listOf(track("snapshot-one", 23.seconds), track("snapshot-two", 29.seconds))
        val saved = JSONObject().apply {
            put("version", 2)
            put("owner", savedOwner)
            put("original", JSONArray(songs.map { it.toJson() }))
            put("order", JSONArray(listOf(0, 1)))
            put("index", 0)
            put("position", 4000)
        }
        check(
            context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit()
                .putString(playbackPreferenceKey(savedOwner), saved.toString()).commit(),
        )
        return testPlaybackController(context, playbackScopes.wrap(scope), owner)
    }

    private fun snapshot(controller: AndroidPlaybackController, state: PlaybackState): Pair<String, String>? =
        ReflectionHelpers.callInstanceMethod(
            controller,
            "snapshot",
            ClassParameter.from(PlaybackState::class.java, state),
        )
    private fun runPlaybackTest(test: suspend TestScope.() -> Unit) = runTest {
        try {
            test()
        } finally {
            playbackScopes.closeSuspending()
        }
    }
}
