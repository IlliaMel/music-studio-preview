package com.illiamelnyk.studio.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.icons.StudioIcons

/** A top-level part of the signed-in app, with its own back stack that starts at [root]. */
enum class TopLevelTab(
    val root: Destination,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    Create(Destination.Create, R.string.nav_create, StudioIcons.AutoAwesome, StudioIcons.AutoAwesomeFilled),
    Listen(Destination.Listen, R.string.nav_listen, StudioIcons.Headphones, StudioIcons.HeadphonesFilled),
    Library(Destination.Library, R.string.nav_library, StudioIcons.LibraryMusic, StudioIcons.LibraryMusicFilled),
    ;

    companion object {
        /** Where the app starts and where back from another tab leads: the user exits through it. */
        val Start = Create
    }
}
