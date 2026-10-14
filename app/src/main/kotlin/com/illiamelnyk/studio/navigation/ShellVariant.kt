package com.illiamelnyk.studio.navigation

import kotlinx.coroutines.flow.Flow

/**
 * How the top level of the signed-in app is laid out. [TopSwitch] is the app's layout; [BottomBar]
 * stays available in debug builds for comparison.
 */
enum class ShellVariant(val tabs: List<TopLevelTab>) {
    /**
     * As in ElevenMusic: a Create | Listen switch at the top, and Library behind a button on Listen,
     * opened on top of it.
     */
    TopSwitch(listOf(TopLevelTab.Create, TopLevelTab.Listen)),

    /** As in the ElevenLabs Android app: Create, Listen and Library in a bar at the bottom. */
    BottomBar(listOf(TopLevelTab.Create, TopLevelTab.Listen, TopLevelTab.Library)),
    ;

    companion object {
        /** What release builds show, and debug builds until switched: ElevenMusic's layout. */
        val Default = TopSwitch
    }
}

/** Which [ShellVariant] the app shows. Release builds always show one; debug builds let it change. */
interface ShellVariantSource {
    val variant: Flow<ShellVariant>
}
