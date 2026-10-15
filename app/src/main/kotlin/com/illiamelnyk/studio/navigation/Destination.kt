package com.illiamelnyk.studio.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every screen of the app. The keys are serializable so the back stacks survive process death;
 * arguments are plain values, so a key never depends on a feature's types.
 */
sealed interface Destination : NavKey {
    /** Sign-in, while nobody is signed in. */
    @Serializable
    data object Welcome : Destination

    @Serializable
    data object Create : Destination

    @Serializable
    data object Listen : Destination

    @Serializable
    data object Library : Destination

    /** A mix, chart or playlist of the catalog. */
    @Serializable
    data class Collection(val id: String) : Destination

    /** A tool of Create, such as Genreshift. */
    @Serializable
    data class Tool(val id: String) : Destination

    /** The full-screen player. */
    @Serializable
    data object Player : Destination

    @Serializable
    data object Profile : Destination
}
