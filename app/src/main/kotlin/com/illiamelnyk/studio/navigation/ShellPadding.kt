package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * What the system bars and the shell's own bars cover, for each kind of screen. Screens draw under
 * the bars, edge to edge, and pass this to their lists as content padding, so the first and last
 * items can scroll out from under them.
 *
 * The bottom is what the shell measured along the bottom edge: the mini player, the bottom bar and
 * the system navigation bar, whichever show. It is read only when a screen lays out, so bars coming
 * and going re-measure the screens without recomposing them.
 *
 * The keyboard is left out on purpose: each screen consumes these paddings, so its `imePadding()`
 * adds only the part of the keyboard that rises above them.
 *
 * @param tabRoot a tab's first screen, under the shell's top bar.
 * @param screen a screen opened on top, which draws its own top bar.
 */
@Immutable
data class ShellPadding(val tabRoot: PaddingValues, val screen: PaddingValues)

/**
 * The height of the shell's bars along the bottom edge, as the shell last measured it. Kept in
 * snapshot state, so the screens that read it lay out again when it changes.
 */
@Stable
class BottomChrome {
    var height: Dp by mutableStateOf(0.dp)
}

@Stable
class TopChrome {
    var height: Dp? by mutableStateOf(null)
}

@Composable
fun rememberShellPadding(bottomChrome: BottomChrome, topChrome: TopChrome): ShellPadding {
    val systemBars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val topBar = StudioTheme.dimens.size.component.topBar
    val none = StudioTheme.dimens.spacing.none
    return remember(systemBars, topBar, bottomChrome, topChrome) {
        ShellPadding(
            tabRoot = ChromePadding(systemBars, top = topBar, bottomChrome = bottomChrome, topChrome = topChrome),
            screen = ChromePadding(systemBars, top = none, bottomChrome = bottomChrome),
        )
    }
}

/** The system bars, plus [top] at the top and at least [bottomChrome] at the bottom. */
@Stable
private class ChromePadding(
    private val systemBars: PaddingValues,
    private val top: Dp,
    private val bottomChrome: BottomChrome,
    private val topChrome: TopChrome? = null,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        systemBars.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp =
        topChrome?.height?.let { max(it, systemBars.calculateTopPadding()) } ?: (systemBars.calculateTopPadding() + top)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        systemBars.calculateRightPadding(layoutDirection)

    // Before the first measurement the bars' height is unknown; the system bar is a safe minimum.
    override fun calculateBottomPadding(): Dp = max(bottomChrome.height, systemBars.calculateBottomPadding())
}
