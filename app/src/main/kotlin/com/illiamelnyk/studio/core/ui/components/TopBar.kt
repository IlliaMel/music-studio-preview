package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * The 56 dp bar at the top of a screen: [navigation] at the start (a [TopBarAvatar] or a back
 * button), then [content] (a [StudioSegmentedControl], for example), and [actions] at the end.
 *
 * It spans the full width and lines its content up with the screen padding, so a title starts where
 * the content below it does. A button smaller than 48 dp keeps its touch area inside that line, so it
 * sits a few dp further in.
 *
 * @param centerContent puts [content] in the middle of the free space, as for a title.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    centerContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dimens = StudioTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.size.component.topBar)
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation()
        if (centerContent) Spacer(Modifier.weight(1f))
        content()
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun AdaptiveTopBar(
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val dimens = StudioTheme.dimens
    FlowRow(
        modifier = modifier.fillMaxWidth().heightIn(min = dimens.size.component.topBar)
            .padding(horizontal = dimens.layout.screenPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs, Alignment.CenterVertically),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation()
            content()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** A [TopBar] with a centered [title] in `titleMedium`. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopBar(modifier = modifier, navigation = navigation, actions = actions, centerContent = true) {
        StudioText(title, StudioTheme.typography.titleMedium, maxLines = 1)
    }
}

/**
 * The user's 32 dp avatar at the start of a [TopBar], which opens the profile.
 *
 * @param seed what the avatar art is generated from until there is a profile picture; see [Cover].
 */
@Composable
fun TopBarAvatar(seed: String, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Cover(
        seed = seed,
        size = dimens.size.avatar.s,
        modifier = modifier
            .minimumTouchTarget()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape = StudioTheme.shapes.full,
    )
}

@PreviewLightDark
@Composable
private fun TopBarPreview() {
    PreviewSurface {
        var mode by remember { mutableStateOf("Create") }
        TopBar(
            navigation = { TopBarAvatar(seed = "Illia", contentDescription = "Profile", onClick = {}) },
            actions = {
                StudioIconButton(StudioIcons.Edit, "New track", onClick = {})
                CreditsCounter(left = 25)
            },
        ) {
            StudioSegmentedControl(listOf("Create", "Listen"), selected = mode, onSelect = { mode = it }) { it }
        }
        TopBar(
            title = "Library",
            navigation = {
                StudioIconButton(StudioIcons.ChevronLeft, "Back", onClick = {}, style = StudioIconButtonStyle.Surface)
            },
            actions = { StudioIconButton(StudioIcons.IosShare, "Share", onClick = {}) },
        )
    }
}
