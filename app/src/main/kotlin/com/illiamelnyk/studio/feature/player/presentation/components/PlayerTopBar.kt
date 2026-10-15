package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.GlassDefaults
import com.illiamelnyk.studio.core.ui.foundation.LocalGlassBackdrop
import com.illiamelnyk.studio.core.ui.foundation.studioGlass
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import com.illiamelnyk.studio.feature.player.presentation.SoonFeature

/**
 * The player's top bar: collapse at the start; share and "more" in one glass pill
 * at the end.
 */
@Composable
fun PlayerTopBar(
    onCollapse: () -> Unit,
    onShare: () -> Unit,
    onComingSoon: (SoonFeature) -> Unit,
    onAddTo: () -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    val dimens = StudioTheme.dimens
    // The buttons' touch areas reach past their circles; the circles line up with the content.
    val touchMargin = (dimens.size.touchTarget - dimens.size.component.iconButton) / 2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.size.component.topBar)
            .padding(horizontal = dimens.spacing.xxl - touchMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(
            icon = StudioIcons.ChevronLeft,
            contentDescription = stringResource(R.string.player_collapse),
            onClick = onCollapse,
            iconRotation = COLLAPSE_ROTATION,
        )
        Spacer(Modifier.weight(1f))
        if (showActions) {
            Box(contentAlignment = Alignment.Center) {
                // The pill is as tall as a button; the buttons' touch areas reach past it.
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(vertical = touchMargin)
                        .studioGlass(LocalGlassBackdrop.current, GlassDefaults.style(StudioTheme.shapes.full)),
                )
                Row {
                    PlayerAction(StudioIcons.IosShare, stringResource(R.string.player_share), onShare)
                    PlayerMoreMenu(onShare = onShare, onComingSoon = onComingSoon, onAddTo = onAddTo)
                }
            }
        }
    }
}

@Composable
private fun PlayerAction(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    StudioIconButton(icon, description, onClick, style = StudioIconButtonStyle.OnGlass)
}

/** The chevron is the kit's "left" one turned to point down. */
private const val COLLAPSE_ROTATION = -90f

@PreviewLightDark
@Composable
private fun PlayerTopBarPreview() {
    PlayerPreviewSurface {
        PlayerTopBar(onCollapse = {}, onShare = {}, onComingSoon = {}, onAddTo = {})
    }
}
