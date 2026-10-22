package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.domain.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Media3AudioEngineTest {
    private val listener = object : AudioEngine.Listener {
        override fun onReady() = Unit
        override fun onEnded() = Unit
        override fun onError(error: AppError) = Unit
        override fun onInterrupted() = Unit
    }

    @Test
    fun sourceReplacementReusesActualExoPlayerAndClearsPreviousPlaylist() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val created = mutableListOf<ExoPlayer>()
        val engine = Media3AudioEngine(context) {
            ExoPlayer.Builder(context).build().also { created += it }
        }
        try {
            engine.prepare(Uri.parse("file:///first.wav"), mapOf("Authorization" to "Bearer first"), listener)
            val player = created.single()
            engine.start()
            engine.clearSource()
            assertEquals(0, player.mediaItemCount)
            assertFalse(player.playWhenReady)
            engine.prepare(Uri.parse("file:///second.wav"), emptyMap(), listener)
            assertEquals(1, created.size)
            assertEquals(1, player.mediaItemCount)
            assertEquals(Uri.parse("file:///second.wav"), player.currentMediaItem?.localConfiguration?.uri)
            assertFalse(player.playWhenReady)
        } finally {
            engine.release()
        }
        engine.release()
        assertTrue(
            runCatching {
                engine.prepare(Uri.parse("file:///third.wav"), emptyMap(), listener)
            }.isFailure,
        )
        assertEquals(1, created.size)
    }
}
