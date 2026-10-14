package com.illiamelnyk.studio.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The theme choice in the app's settings file. It is a device setting, so it never goes to the backend. */
class DataStoreThemePreferenceRepository(private val dataStore: DataStore<Preferences>) : ThemePreferenceRepository {

    override val preference: Flow<ThemePreference> = dataStore.data
        // An unreadable file falls back to the default rather than taking the app's theme down with it.
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            val stored = preferences[THEME]
            ThemePreference.entries.firstOrNull { it.name == stored } ?: ThemePreference.System
        }
        .distinctUntilChanged()

    override suspend fun set(preference: ThemePreference) {
        dataStore.edit { it[THEME] = preference.name }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
