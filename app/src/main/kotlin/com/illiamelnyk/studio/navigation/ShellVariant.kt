package com.illiamelnyk.studio.navigation

import kotlinx.coroutines.flow.Flow

/**
 * How the top level of the signed-in app is laid out. Both are built so they can be compared on a
 * device; debug builds switch between them in the profile.
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
        /** What release builds show, and debug builds until switched: the familiar Android layout. */
        val Default = BottomBar
    }
}

/** Which [ShellVariant] the app shows. Release builds always show one; debug builds let it change. */
interface ShellVariantSource {
    val variant: Flow<ShellVariant>
}
