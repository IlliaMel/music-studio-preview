package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.domain.playback.RepeatMode
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioSessionPlayerTest {
    @get:Rule val main = MainDispatcherRule()
    private val scopes = PlaybackTestScopes(main.testDispatcher.scheduler)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @org.junit.After fun cleanup() {
        scopes.close()
        context.getSharedPreferences("playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun systemControlsUseDomainQueueAndPublishOnlyOpaqueSanitizedItems() = runPlaybackTest {
        val playback = testPlaybackController(context, scopes.wrap(backgroundScope), { "session-owner" })
        val songs = listOf("private-a", "private-b").map {
            track(it, 20.seconds).copy(audioUri = "https://private.example/$it")
        }
        playback.play(songs)
        val system = StudioSessionPlayer(playback)
        try {
            assertEquals(2, system.mediaItemCount)
            for (index in songs.indices) {
                val item = system.getMediaItemAt(index)
                assertFalse(item.mediaId.contains(songs[index].id.value))
                assertNull(item.localConfiguration)
                assertNull(item.requestMetadata.mediaUri)
                assertNull(item.mediaMetadata.extras)
            }
            system.pause()
            assertFalse(playback.state.value.isPlaying)
            system.play()
            assertTrue(playback.state.value.isPlaying)
            system.seekToNextMediaItem()
            assertEquals(songs[1].id, playback.state.value.current?.id)
            system.seekTo(4500)
            assertEquals(4500L, playback.state.value.positionMs)
            system.seekToPreviousMediaItem()
            assertEquals(0L, playback.state.value.positionMs)
            assertEquals(songs[1].id, playback.state.value.current?.id)
            system.seekToPreviousMediaItem()
            assertEquals(songs[0].id, playback.state.value.current?.id)
            system.repeatMode = Player.REPEAT_MODE_ALL
            assertEquals(RepeatMode.All, playback.state.value.repeat)
            system.shuffleModeEnabled = true
            assertTrue(playback.state.value.shuffle)
        } finally {
            system.release()
            playback.stop()
        }
    }

    @Test
    fun systemPauseStopsStationInsteadOfCreatingAnUnsupportedPausedRadioState() = runPlaybackTest {
        val playback = testPlaybackController(context, scopes.wrap(backgroundScope), { "station-stop-owner" })
        playback.playStation("Radio", listOf(track("radio", 20.seconds)))
        val system = StudioSessionPlayer(playback)
        try {
            system.pause()
            assertNull(playback.state.value.current)
            assertNull(playback.systemSnapshot())
        } finally {
            system.release()
            playback.stop()
        }
    }

    @Test
    fun stationControlsAndStaleOwnerCannotSeekOrResumePrivateQueue() = runPlaybackTest {
        var owner: String? = "station-owner"
        val playback = testPlaybackController(context, scopes.wrap(backgroundScope), { owner })
        playback.playStation("Radio", listOf(track("station-private", 20.seconds)))
        val system = StudioSessionPlayer(playback)
        try {
            assertFalse(system.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            assertFalse(system.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
            system.seekTo(5000)
            assertEquals(0L, playback.state.value.positionMs)
            owner = null
            system.refresh()
            assertEquals(0, system.mediaItemCount)
            assertFalse(system.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
            system.play()
            assertNull(playback.systemSnapshot())
        } finally {
            system.release()
            playback.stop()
        }
    }
    private fun runPlaybackTest(test: suspend TestScope.() -> Unit) = runTest {
        try {
            test()
        } finally {
            scopes.closeSuspending()
        }
    }
}
