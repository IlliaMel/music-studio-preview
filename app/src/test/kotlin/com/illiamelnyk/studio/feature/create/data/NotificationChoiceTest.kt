package com.illiamelnyk.studio.feature.create.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationChoiceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `answer is persisted and visible to a newly constructed preference reader`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "notification.preferences_pb")
        }
        val first = DataStoreNotificationChoice(store)
        assertFalse(first.hasAnswered())
        first.answer()
        assertTrue(DataStoreNotificationChoice(store).hasAnswered())
    }
}
