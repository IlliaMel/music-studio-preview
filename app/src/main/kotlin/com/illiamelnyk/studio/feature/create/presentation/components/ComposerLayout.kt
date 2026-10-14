package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The screen's [content] with the [composer] pinned over its bottom edge. The composer is measured
 * first, keyboard and bars included, and the content gets that height as [ComposerSpace] in the
 * same pass, so a list can scroll its last items out from under the composer without lagging a
 * frame behind while the keyboard moves.
 */
@Composable
fun ComposerLayout(
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (space: ComposerSpace) -> Unit,
) {
    val space = remember { ComposerSpace() }
    Layout(contents = listOf({ content(space) }, composer), modifier = modifier) { (screen, bottom), constraints ->
        val composerConstraints = constraints.copy(minHeight = 0)
        val composerPlaceables = bottom.map { it.measure(composerConstraints) }
        val composerHeight = composerPlaceables.maxOfOrNull { it.height } ?: 0
        space.height = composerHeight.toDp()
        val screenPlaceables = screen.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            screenPlaceables.forEach { it.place(0, 0) }
            composerPlaceables.forEach { it.place(0, constraints.maxHeight - composerHeight) }
        }
    }
}

/** The height the composer takes at the bottom, as last measured; read it while laying out. */
@Stable
class ComposerSpace {
    var height: Dp by mutableStateOf(0.dp)
        internal set
}

/** These paddings at the top only, read when laying out like the original's. */
fun PaddingValues.onlyTop(): PaddingValues = TopOnly(this)

@Stable
private class TopOnly(private val padding: PaddingValues) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateTopPadding(): Dp = padding.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateBottomPadding(): Dp = 0.dp

    override fun equals(other: Any?): Boolean = other is TopOnly && other.padding == padding

    override fun hashCode(): Int = padding.hashCode()
}

/**
 * [screen]'s paddings with the composer's height at the bottom (it already holds the bottom bars)
 * and [extraTop] below the top. Read when the list lays out, so the composer and the keyboard
 * moving re-lay the list without recomposing it.
 */
@Stable
class ComposerAwarePadding(
    private val screen: PaddingValues,
    private val space: ComposerSpace,
    private val extraTop: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        screen.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = screen.calculateTopPadding() + extraTop

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        screen.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding(): Dp = space.height
}
