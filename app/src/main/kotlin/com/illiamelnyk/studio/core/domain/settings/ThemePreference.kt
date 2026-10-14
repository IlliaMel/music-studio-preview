package com.illiamelnyk.studio.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** Which color scheme the user wants. */
enum class ThemePreference {
    /** Follow the system's dark theme setting. */
    System,
    Light,
    Dark,
}

/** The theme choice, kept on this device. */
interface ThemePreferenceRepository {
    /** The stored choice, [ThemePreference.System] until the user makes one. */
    val preference: Flow<ThemePreference>

    suspend fun set(preference: ThemePreference)
}
