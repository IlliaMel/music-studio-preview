package com.illiamelnyk.studio.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * What the system bars and the shell's own bars cover, for each kind of screen. Screens draw under
 * the bars, edge to edge, and pass this to their lists as content padding, so the first and last
 * items can scroll out from under them.
 *
 * The keyboard is left out on purpose: the shell consumes these paddings, so a screen's
 * `imePadding()` adds only the part of the keyboard that rises above them.
 *
 * @param tabRoot a tab's first screen, under the shell's top bar and, in [ShellVariant.BottomBar],
 *   above its bottom bar.
 * @param screen a screen opened on top, which draws its own top bar.
 * @param fullScreen the player, which hides the mini player too.
 */
@Immutable
data class ShellPadding(val tabRoot: PaddingValues, val screen: PaddingValues, val fullScreen: PaddingValues)

@Composable
fun shellPadding(variant: ShellVariant, showsMiniPlayer: Boolean): ShellPadding {
    val dimens = StudioTheme.dimens
    val systemBars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val none = dimens.spacing.none
    val miniPlayer = if (showsMiniPlayer) dimens.size.component.miniPlayer + dimens.spacing.xs * 2 else none
    val bottomBar = if (variant ==
        ShellVariant.BottomBar
    ) {
        dimens.size.component.navigationBar + dimens.spacing.xs
    } else {
        none
    }
    return ShellPadding(
        tabRoot = systemBars + PaddingValues(top = dimens.size.component.topBar, bottom = bottomBar + miniPlayer),
        screen = systemBars + PaddingValues(bottom = miniPlayer),
        fullScreen = systemBars,
    )
}
