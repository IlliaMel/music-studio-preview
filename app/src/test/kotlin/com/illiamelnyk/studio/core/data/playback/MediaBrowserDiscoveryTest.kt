package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.os.Bundle
import android.service.media.MediaBrowserService.BrowserRoot
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
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
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class MediaBrowserDiscoveryTest {
    @get:Rule
    val main = MainDispatcherRule()
    private val playbackScopes = PlaybackTestScopes(main.testDispatcher.scheduler)

    @org.junit.After
    fun closePlaybackScopes() {
        playbackScopes.close()
    }

    @Test
    fun trustedRecentDiscoveryRestoresPausedWithoutForegroundOrAudioAndRejectsOwnerLoss() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        var owner: String? = "owner-a"
        val song = track("private-recent", 20.seconds)
        val saved = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray().put(song.toJson())).put("order", JSONArray().put(0))
            .put("index", 0).put("position", 4_250)
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        preferences.edit().putString(playbackPreferenceKey("owner-a"), saved.toString()).commit()
        val playback = AndroidPlaybackController(context, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        startKoin { modules(module { single<PlaybackController> { playback } }) }
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val browser = service.get()
            val hint = Bundle().apply { putBoolean(BrowserRoot.EXTRA_RECENT, true) }
            val root = browser.onGetRoot(context.packageName, context.applicationInfo.uid, hint)
            assertNotNull(root)
            assertEquals(setOf(BrowserRoot.EXTRA_RECENT), requireNotNull(root).extras?.keySet())
            assertFalse(root.rootId.contains(song.id.value))
            assertFalse(playback.state.value.isPlaying)
            assertEquals(4_250L, playback.state.value.positionMs)
            assertNull(shadowOf(browser).lastForegroundNotification)
            assertNull(browser.onGetRoot("untrusted.media", 2001, hint))
            owner = null
            assertNull(browser.onGetRoot(context.packageName, context.applicationInfo.uid, hint))
            assertFalse(resumeSystemPlayback(playback, root.rootId.removePrefix("recent:")))
        } finally {
            service.destroy()
            playback.stop()
            playbackScopes.close()
            preferences.edit().clear().commit()
            stopKoin()
        }
    }

    @Test
    fun shutdownPersistenceFailureStillReleasesSessionAndRemovesForegroundNotification() = runTest {
        val delegate = FakePlaybackController(backgroundScope)
        delegate.play(listOf(track("shutdown-failure", 20.seconds)))
        var shutdownCalls = 0
        val playback = object : PlaybackController by delegate {
            override fun releaseForServiceShutdown() {
                shutdownCalls++
                throw IOException("Private fixture message must not be logged")
            }
        }
        startKoin { modules(module { single<PlaybackController> { playback } }) }
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        var destroyed = false
        try {
            val browser = service.get()
            val session = ReflectionHelpers.getField<android.media.session.MediaSession>(browser, "session")
            session.isActive = true
            browser.onStartCommand(null, 0, 1)
            val shadow = shadowOf(browser)
            assertTrue(shadow.isLastForegroundNotificationAttached)
            service.destroy()
            destroyed = true
            assertEquals(1, shutdownCalls)
            assertFalse(session.isActive)
            assertTrue(shadow.isForegroundStopped)
            assertTrue(shadow.notificationShouldRemoved)
            assertFalse(shadow.isLastForegroundNotificationAttached)
            val logs = org.robolectric.shadows.ShadowLog.getLogsForTag("StudioPlayback")
            assertTrue(logs.any { it.msg == "Shutdown persistence failed: IOException" })
            assertFalse(logs.any { it.msg.contains("Private fixture message") || it.throwable != null })
        } finally {
            if (!destroyed) service.destroy()
            delegate.stop()
            stopKoin()
        }
    }
}
