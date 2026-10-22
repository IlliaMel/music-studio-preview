package com.illiamelnyk.studio.navigation

import androidx.annotation.StringRes
import com.illiamelnyk.studio.R

/** A top-level part of the signed-in app, with its own back stack that starts at [root]. */
enum class TopLevelTab(val root: Destination, @param:StringRes val label: Int) {
    Create(Destination.Create, R.string.nav_create),
    Listen(Destination.Listen, R.string.nav_listen),

    /** Kept as a saved-state key for older versions; Library is now a screen above Listen. */
    Library(Destination.Library, R.string.nav_library),
    ;

    companion object {
        val primaryTabs = listOf(Create, Listen)

        /** Where the app starts and where back from another tab leads: the user exits through it. */
        val Start = Create
    }
}
