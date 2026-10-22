package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.ListenableFuture
import com.illiamelnyk.studio.core.data.fake.FakePlaybackController
import com.illiamelnyk.studio.core.data.local.toJson
import com.illiamelnyk.studio.core.domain.playback.PlaybackController
import com.illiamelnyk.studio.feature.player.fake.track
import com.illiamelnyk.studio.testing.MainDispatcherRule
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
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
    fun directInAppPlaybackRegistersTheSessionWithoutAnExternalControllerConnection() = runPlaybackTest {
        val playback = FakePlaybackController(backgroundScope)
        playback.play(listOf(track("direct-ui-playback", 20.seconds)))
        startKoin {
            modules(
                module {
                    single<PlaybackController> { playback }
                    single<PlaybackReadiness> { PlaybackReadiness {} }
                },
            )
        }
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val browser = service.get()
            // No onGetSession call: this is how UI playback starts the service in production.
            assertEquals(1, browser.sessions.size)
            val session = browser.sessions.single()
            assertTrue(session.player.playWhenReady)
            assertEquals(playback.systemSnapshot()?.mediaId, session.player.currentMediaItem?.mediaId)
            val context: Context = ApplicationProvider.getApplicationContext()
            browser.onStartCommand(Intent(context, PlaybackService::class.java), 0, 1)
            assertEquals(listOf(session), browser.sessions)
        } finally {
            service.destroy()
            playback.stop()
            stopKoin()
        }
    }

    @Test
    fun trustedRecentDiscoveryRestoresPausedWithoutForegroundOrAudioAndRejectsOwnerLoss() = runPlaybackTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        var owner: String? = "owner-a"
        val song = track("private-recent", 20.seconds)
        val saved = JSONObject().put("version", 2).put("owner", owner)
            .put("original", JSONArray().put(song.toJson())).put("order", JSONArray().put(0))
            .put("index", 0).put("position", 4_250)
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        preferences.edit().putString(playbackPreferenceKey("owner-a"), saved.toString()).commit()
        val playback = testPlaybackController(context, playbackScopes.wrap(backgroundScope), ownerId = { owner })
        startKoin {
            modules(
                module {
                    single<PlaybackController> { playback }
                    single<PlaybackReadiness> { PlaybackReadiness {} }
                },
            )
        }
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val browser = service.get()
            val caller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                context.packageName,
                android.os.Process.myPid(),
                context.applicationInfo.uid,
                0,
                0,
                true,
                Bundle.EMPTY,
                true,
            )
            val session = requireNotNull(browser.onGetSession(caller))
            val params = LibraryParams.Builder().setRecent(true).build()
            val result = browser.libraryCallback.onGetLibraryRoot(session, caller, params).result()
            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            val root = requireNotNull(result.value)
            assertFalse(root.mediaId.contains(song.id.value))
            val children = browser.libraryCallback.onGetChildren(session, caller, root.mediaId, 0, 10, params).result()
            assertEquals(1, requireNotNull(children.value).size)
            assertNull(children.value!!.single().localConfiguration)
            val unauthorizedSource = browser.libraryCallback.onSetMediaItems(
                session,
                caller,
                listOf(MediaItem.fromUri("https://untrusted.example/audio")),
                0,
                0,
            )
            assertTrue(runCatching { unauthorizedSource.result() }.isFailure)
            assertFalse(playback.state.value.isPlaying)
            assertEquals(4_250L, playback.state.value.positionMs)
            assertNull(shadowOf(browser).lastForegroundNotification)
            val untrusted = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                "untrusted.media",
                0,
                2001,
                0,
                0,
                false,
                Bundle.EMPTY,
                true,
            )
            assertNull(browser.onGetSession(untrusted))
            owner = null
            val unavailable = browser.libraryCallback.onGetLibraryRoot(session, caller, params).result()
            assertNull(unavailable.value)
            val unavailableResumption = browser.libraryCallback.onPlaybackResumption(session, caller, true)
            runCurrent()
            assertTrue(runCatching { unavailableResumption.result() }.isFailure)
            val staleChildren = browser.libraryCallback.onGetChildren(
                session,
                caller,
                root.mediaId,
                0,
                10,
                params,
            ).result()
            assertTrue(requireNotNull(staleChildren.value).isEmpty())
            assertFalse(resumeSystemPlayback(playback, root.mediaId.removePrefix("recent:")))
        } finally {
            service.destroy()
            playback.stop()
            playbackScopes.closeSuspending()
            preferences.edit().clear().commit()
            stopKoin()
        }
    }

    @Test
    fun coldServiceDiscoveryAndResumptionAwaitOwnerStartupWithoutDecoding() = runPlaybackTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val ownerId = "cold-service-owner"
        val song = track("cold-service-track", 20.seconds)
        val saved = JSONObject().put("version", 2).put("owner", ownerId)
            .put("original", JSONArray().put(song.toJson())).put("order", JSONArray().put(0))
            .put("index", 0).put("position", 4_250)
        val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        preferences.edit().putString(playbackPreferenceKey(ownerId), saved.toString()).commit()
        var owner: String? = null
        var decoderCreations = 0
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val playback = testPlaybackController(
            context,
            playbackScopes.wrap(backgroundScope),
            { owner },
            engineFactory = {
                decoderCreations++
                FakeAudioEngine()
            },
        )
        startKoin {
            modules(
                module {
                    single<PlaybackController> { playback }
                    single<PlaybackReadiness> { PlaybackReadiness { ready.await() } }
                },
            )
        }
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        try {
            val browser = service.get()
            val caller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                context.packageName,
                android.os.Process.myPid(),
                context.applicationInfo.uid,
                0,
                0,
                true,
                Bundle.EMPTY,
                true,
            )
            val session = requireNotNull(browser.onGetSession(caller))
            val rootFuture = browser.libraryCallback.onGetLibraryRoot(session, caller, null)
            val resumeFuture = browser.libraryCallback.onPlaybackResumption(session, caller, true)
            runCurrent()
            assertFalse(rootFuture.isDone)
            assertFalse(resumeFuture.isDone)
            assertTrue(playback.state.value.queue.isEmpty())
            owner = ownerId
            ready.complete(Unit)
            val root = requireNotNull(rootFuture.result().value)
            val resumed = resumeFuture.result()
            assertEquals(4_250L, resumed.startPositionMs)
            assertEquals(song.id, playback.state.value.current?.id)
            assertFalse(playback.state.value.isPlaying)
            assertEquals(0, decoderCreations)
            assertNull(shadowOf(browser).lastForegroundNotification)
            val children = browser.libraryCallback.onGetChildren(session, caller, root.mediaId, 0, 10, null).result()
            assertEquals(1, requireNotNull(children.value).size)
            assertNull(children.value!!.single().localConfiguration)
            owner = null
            val denied = browser.libraryCallback.onPlaybackResumption(session, caller, true)
            assertTrue(runCatching { denied.result() }.isFailure)
        } finally {
            service.destroy()
            playback.stop()
            PlaybackPersistenceWriter.shared.flush()
            preferences.edit().clear().commit()
            stopKoin()
        }
    }

    @Test
    fun shutdownPersistenceFailureStillReleasesSessionAndRemovesForegroundNotification() = runPlaybackTest {
        val delegate = FakePlaybackController(backgroundScope)
        delegate.play(listOf(track("shutdown-failure", 20.seconds)))
        var shutdownCalls = 0
        val playback = object : PlaybackController by delegate {
            override fun releaseForServiceShutdown() {
                shutdownCalls++
                throw IOException("Private fixture message must not be logged")
            }
        }
        startKoin {
            modules(
                module {
                    single<PlaybackController> { playback }
                    single<PlaybackReadiness> { PlaybackReadiness {} }
                },
            )
        }
        val service = Robolectric.buildService(PlaybackService::class.java).create()
        var destroyed = false
        try {
            val browser = service.get()
            val session = ReflectionHelpers.getField<MediaSession>(browser, "session")
            val shadow = shadowOf(browser)
            service.destroy()
            destroyed = true
            assertEquals(1, shutdownCalls)
            assertTrue(ReflectionHelpers.callInstanceMethod<Boolean>(session, "isReleased"))
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
    private fun <T> ListenableFuture<T>.result(): T {
        main.testDispatcher.scheduler.runCurrent()
        return get()
    }
    private fun runPlaybackTest(test: suspend TestScope.() -> Unit) = runTest {
        try {
            test()
        } finally {
            playbackScopes.closeSuspending()
        }
    }
}
