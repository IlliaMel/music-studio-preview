package com.illiamelnyk.studio.core.data.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationPreferences
import com.illiamelnyk.studio.core.domain.notification.GenerationNotificationTarget
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidGenerationNotificationsTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var enabled = true
    private var failRead = false
    private val choice = object : GenerationNotificationPreferences {
        override suspend fun hasAnswered() = true
        override suspend fun answer(enabled: Boolean) {
            this@AndroidGenerationNotificationsTest.enabled = enabled
        }
        override suspend fun isEnabled(): Boolean {
            if (failRead) throw java.io.IOException("simulated preference failure")
            return enabled
        }
    }
    private fun track(status: TrackStatus) = FakeCatalog.tracks.first().copy(
        id = TrackId("specific-track"),
        title = "Specific piano",
        status = status,
    )

    @Before
    fun clean() {
        context.getSharedPreferences("generation-notifications", Context.MODE_PRIVATE).edit().clear().commit()
        manager.cancelAll()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @Test
    fun readyNotificationTargetsTheExactTrackAndRemainsDeduplicatedAfterRestartAndDismissal() = runTest {
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        notifier.deliver("owner")
        val notification = manager.activeNotifications.single().notification
        assertEquals(
            context.getString(R.string.generation_notification_ready),
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals("owner", intent.getStringExtra(GenerationNotificationTarget.OWNER))
        assertEquals("specific-track", intent.getStringExtra(GenerationNotificationTarget.TRACK))
        manager.cancelAll()

        val restarted = AndroidGenerationNotifications(context, choice)
        restarted.record("owner", listOf(track(TrackStatus.Ready)))
        restarted.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun failedGenerationHasItsOwnNotificationAndTrackTarget() = runTest {
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Failed(AppError.Network))))
        notifier.deliver("owner")
        val notification = manager.activeNotifications.single().notification
        assertEquals(
            context.getString(R.string.generation_notification_failed),
            notification.extras.getString(Notification.EXTRA_TITLE),
        )
        assertEquals(
            "specific-track",
            shadowOf(notification.contentIntent).savedIntent
                .getStringExtra(GenerationNotificationTarget.TRACK),
        )
    }

    @Test
    fun deniedPermissionNeverPostsOrReplaysOldCompletionsAfterPermissionIsGranted() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        notifier.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun explicitOptOutNeverPosts() = runTest {
        enabled = false
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        notifier.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun signOutClearsVisibleNotificationsAndStaleOwnerDeliveryCannotCrossAccounts() = runTest {
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        notifier.deliver("owner")
        assertEquals(1, manager.activeNotifications.size)
        notifier.record(null, emptyList())
        assertTrue(manager.activeNotifications.isEmpty())
        notifier.record("different-owner", emptyList())
        notifier.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun cachedReadyHistoryDoesNotGenerateNotificationsAndDeletingOwnerRemovesLedger() = runTest {
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        notifier.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.forget("owner")
        val stored = context.getSharedPreferences("generation-notifications", Context.MODE_PRIVATE).all
        assertTrue(stored.isEmpty())
    }

    @Test
    fun processRestartCanFinishAPersistedPendingTrackWithoutLosingTheCompletion() = runTest {
        AndroidGenerationNotifications(context, choice).record("owner", listOf(track(TrackStatus.Queued)))
        val restarted = AndroidGenerationNotifications(context, choice)
        restarted.record("owner", listOf(track(TrackStatus.Ready)))
        restarted.deliver("owner")
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun preferenceIoFailureLeavesCompletionPendingForASafeLaterRetry() = runTest {
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        failRead = true
        try {
            notifier.deliver("owner")
            org.junit.Assert.fail("Expected simulated IO failure")
        } catch (_: java.io.IOException) {
            assertTrue(manager.activeNotifications.isEmpty())
        }
        failRead = false
        notifier.deliver("owner")
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun realRetryOfTheSameTrackNotifiesNewCompletionWithoutResettingOnRepeatedPendingSnapshots() = runTest {
        val notifier = AndroidGenerationNotifications(context, choice)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Failed(AppError.Network))))
        notifier.deliver("owner")
        assertEquals(1, manager.activeNotifications.size)
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        assertTrue(manager.activeNotifications.isEmpty())
        notifier.record("owner", listOf(track(TrackStatus.Queued)))
        notifier.record("owner", listOf(track(TrackStatus.Ready)))
        notifier.deliver("owner")
        assertEquals(
            context.getString(R.string.generation_notification_ready),
            manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TITLE),
        )
        manager.cancelAll()
        val restarted = AndroidGenerationNotifications(context, choice)
        restarted.record("owner", listOf(track(TrackStatus.Ready)))
        restarted.deliver("owner")
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
