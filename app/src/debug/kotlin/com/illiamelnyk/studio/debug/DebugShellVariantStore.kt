package com.illiamelnyk.studio.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.illiamelnyk.studio.navigation.ShellVariant
import com.illiamelnyk.studio.navigation.ShellVariantSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The shell variant picked in the debug settings, kept in a debug-only preferences file. */
class DebugShellVariantStore(private val dataStore: DataStore<Preferences>) : ShellVariantSource {

    override val variant: Flow<ShellVariant> = dataStore.data
        .map { preferences ->
            val stored = preferences[VARIANT]
            ShellVariant.entries.firstOrNull { it.name == stored } ?: ShellVariant.Default
        }
        .distinctUntilChanged()

    suspend fun set(variant: ShellVariant) {
        dataStore.edit { it[VARIANT] = variant.name }
    }

    private companion object {
        val VARIANT = stringPreferencesKey("shell_variant")
    }
}
