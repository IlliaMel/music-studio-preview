package com.illiamelnyk.studio.testing

import com.illiamelnyk.studio.core.domain.settings.ThemePreference
import com.illiamelnyk.studio.core.domain.settings.ThemePreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The theme choice in memory, starting at [ThemePreference.System] like the real one. */
class InMemoryThemePreferenceRepository : ThemePreferenceRepository {
    override val preference: StateFlow<ThemePreference> = MutableStateFlow(ThemePreference.System)

    override suspend fun set(preference: ThemePreference) {
        (this.preference as MutableStateFlow).value = preference
    }
}
