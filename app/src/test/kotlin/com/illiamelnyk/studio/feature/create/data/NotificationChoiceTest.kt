package com.illiamelnyk.studio.feature.create.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationChoiceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun answerIsPersistedAndVisibleToANewlyConstructedPreferenceReader() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(folder.root, "notification.preferences_pb")
        }
        val first = DataStoreNotificationChoice(store)
        assertFalse(first.hasAnswered())
        first.answer(true)
        assertTrue(DataStoreNotificationChoice(store).hasAnswered())
        assertTrue(DataStoreNotificationChoice(store).isEnabled())
    }

    @Test
    fun legacyAnsweredOnlyValueIsUnknownConsentAndCannotEnableNotifications() = runTest {
        val values = MutableStateFlow<Preferences>(
            preferencesOf(booleanPreferencesKey("create_notification_answered") to true),
        )
        val store = object : DataStore<Preferences> {
            override val data = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(values.value).also { values.value = it }
        }
        val reader = DataStoreNotificationChoice(store)
        assertFalse(reader.hasAnswered())
        assertFalse(reader.isEnabled())
        reader.answer(false)
        assertTrue(DataStoreNotificationChoice(store).hasAnswered())
        assertFalse(DataStoreNotificationChoice(store).isEnabled())
    }
}
