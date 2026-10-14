package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.foundation.GlassDefaults
import com.illiamelnyk.studio.core.ui.foundation.LocalGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.minimumTouchTarget
import com.illiamelnyk.studio.core.ui.foundation.studioGlass
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * A glass circle with an icon, 40 dp unless [size] says otherwise, for the player's buttons,
 * blurring the [LocalGlassBackdrop]. The kit's icon button has no glass style yet, so the player
 * builds it here from the kit's glass and touch target.
 *
 * With [selected] it is a switch: on, it turns into the inverse pill, like a selected chip.
 *
 * @param iconRotation turns the icon, in degrees: the kit has no "collapse" chevron pointing down.
 * @param size of the circle; the touch area is at least 48 dp whatever it is.
 */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    iconRotation: Float = 0f,
    size: Dp = StudioTheme.dimens.size.component.iconButton,
) {
    val colors = StudioTheme.colors
    val shape = StudioTheme.shapes.full
    val interaction = if (selected == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(value = selected, role = Role.Switch, onValueChange = { onClick() })
    }
    val surface = if (selected == true) {
        Modifier.background(colors.inverse.surface, shape)
    } else {
        Modifier.studioGlass(LocalGlassBackdrop.current, GlassDefaults.style(shape))
    }
    Box(
        // clickable comes before the container so that the press scale shrinks the whole circle.
        modifier = modifier
            .minimumTouchTarget()
            .then(interaction)
            .size(size)
            .then(surface),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(
            icon = icon,
            contentDescription = contentDescription,
            modifier = Modifier.rotate(iconRotation),
            tint = if (selected == true) colors.inverse.content else colors.overlay.onGlass,
        )
    }
}

@PreviewLightDark
@Composable
private fun GlassIconButtonPreview() {
    PreviewScreen {
        Row {
            GlassIconButton(StudioIcons.ChevronLeft, "Close", onClick = {}, iconRotation = -90f)
            GlassIconButton(StudioIcons.Forum, "Lyrics", onClick = {}, selected = false)
            GlassIconButton(StudioIcons.Forum, "Lyrics", onClick = {}, selected = true)
        }
    }
}
