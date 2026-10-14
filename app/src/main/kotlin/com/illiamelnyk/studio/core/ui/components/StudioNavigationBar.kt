package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** A destination of [StudioNavigationBar]; [selectedIcon] is usually the filled version of [icon]. */
@Immutable
data class StudioNavigationItem(
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int = icon,
)

/**
 * The bottom bar with the top-level destinations: an icon in a 56 × 32 dp indicator over a label.
 * The selected destination gets a `surface.level4` indicator and its filled icon. TalkBack reads
 * the items as tabs.
 *
 * It spans the screen, with a hairline along its top, and runs behind the system navigation bar:
 * it pads itself for the navigation bar insets (bottom and sides) and keeps its 80 dp of
 * destinations above them, so place it at the bottom edge of an edge-to-edge screen and don't pad
 * it for the insets again.
 */
@Composable
fun StudioNavigationBar(
    items: List<StudioNavigationItem>,
    selectedIndex: Int,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val systemBar = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    Row(
        modifier = modifier
            .selectableGroup()
            .fillMaxWidth()
            .background(colors.surface.level1)
            .drawBehind {
                drawRect(colors.outline.subtle, size = Size(size.width, dimens.stroke.hairline.toPx()))
            }
            .windowInsetsPadding(systemBar)
            .height(dimens.size.component.navigationBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            NavigationItem(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavigationItem(
    item: StudioNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val motion = StudioTheme.motion
    val animationSpec = tween<Color>(motion.duration.short, easing = motion.easing.standard)
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) colors.surface.level4 else Color.Transparent,
        animationSpec = animationSpec,
        label = "indicator",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.content.primary else colors.content.secondary,
        animationSpec = animationSpec,
        label = "content",
    )
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = dimens.size.control.xl, height = dimens.size.control.s)
                .background(indicatorColor, StudioTheme.shapes.full),
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(
                icon = if (selected) item.selectedIcon else item.icon,
                contentDescription = null,
                tint = contentColor,
            )
        }
        StudioText(item.label, StudioTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}

// Without the preview padding: the bar spans the screen.
@PreviewLightDark
@Composable
private fun StudioNavigationBarPreview() {
    StudioTheme {
        var selected by remember { mutableIntStateOf(0) }
        StudioNavigationBar(
            items = listOf(
                StudioNavigationItem("Create", StudioIcons.AutoAwesome, StudioIcons.AutoAwesomeFilled),
                StudioNavigationItem("Listen", StudioIcons.Headphones, StudioIcons.HeadphonesFilled),
                StudioNavigationItem("Library", StudioIcons.LibraryMusic, StudioIcons.LibraryMusicFilled),
            ),
            selectedIndex = selected,
            onSelect = { selected = it },
        )
    }
}
