package com.illiamelnyk.studio.feature.create.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.illiamelnyk.studio.feature.create.domain.NotificationChoice
import kotlinx.coroutines.flow.first

class DataStoreNotificationChoice(private val store: DataStore<Preferences>) : NotificationChoice {
    override suspend fun hasAnswered(): Boolean = store.data.first()[ENABLED] != null

    override suspend fun isEnabled(): Boolean = store.data.first()[ENABLED] == true

    override suspend fun answer(enabled: Boolean) {
        store.edit {
            it[ENABLED] = enabled
            it[ANSWERED] = true
        }
    }

    private companion object {
        val ANSWERED = booleanPreferencesKey("create_notification_answered")
        val ENABLED = booleanPreferencesKey("generation_notifications_enabled")
    }
}
