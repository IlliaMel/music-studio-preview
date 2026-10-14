package com.illiamelnyk.studio.core.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.illiamelnyk.studio.core.ui.foundation.LocalStudioContentColor
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * One of [StudioIcons], tinted with the current content color unless [tint] is given.
 *
 * @param contentDescription what the icon means to someone who can't see it, or `null` when it only
 *   decorates a text that already says it.
 * @param size one of `StudioTheme.dimens.size.icon`; 24 dp by default.
 */
@Composable
fun StudioIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalStudioContentColor.current,
    size: Dp = StudioTheme.dimens.size.icon.l,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = remember(tint) { ColorFilter.tint(tint) },
    )
}
