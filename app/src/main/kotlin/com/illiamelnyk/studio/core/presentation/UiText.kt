package com.illiamelnyk.studio.core.presentation

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Text for the UI that a ViewModel decides on but can't resolve itself, because resources need a
 * context: a string resource with its arguments, or text that comes as it is (a track title).
 * Resolve it with [asString].
 */
@Immutable
sealed interface UiText {
    /** Text shown as it is, such as a title the user typed. */
    data class Raw(val value: String) : UiText

    /** A string resource; [args] fill its placeholders and may be [UiText]s themselves. */
    data class Resource(@param:StringRes val id: Int, val args: ImmutableList<Any> = persistentListOf()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toImmutableList())
    }

    /** A plurals resource for [count]; [args] fill its placeholders. */
    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: ImmutableList<Any> = persistentListOf(),
    ) : UiText {
        constructor(@PluralsRes id: Int, count: Int, vararg args: Any) : this(id, count, args.toImmutableList())
    }
}

/** Resolves the text, for code outside composition such as an effect handler. */
fun UiText.asString(resources: Resources): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> resources.getString(id, *args.resolve(resources))
    is UiText.Plural -> resources.getQuantityString(id, count, *args.resolve(resources))
}

/** Resolves the text in composition. */
@Composable
@ReadOnlyComposable
fun UiText.asString(): String = asString(LocalResources.current)

private fun List<Any>.resolve(resources: Resources): Array<Any> =
    map { arg -> if (arg is UiText) arg.asString(resources) else arg }.toTypedArray()
