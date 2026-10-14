package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/** An artist in a carousel: a 64 dp round picture with the name under it. */
@Composable
fun ArtistAvatar(
    name: String,
    seed: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.clickable(
            interactionSource = null,
            indication = studioIndication(StudioTheme.shapes.m),
            role = Role.Button,
            onClick = onClick,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(seed, dimens.size.avatar.l, shape = StudioTheme.shapes.full)
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioText(name, StudioTheme.typography.labelMedium, maxLines = 1)
            if (verified) VerifiedMark()
        }
    }
}

@PreviewLightDark
@Composable
private fun ArtistAvatarPreview() {
    PreviewSurface {
        ArtistAvatar("Lei Swan", "Lei Swan", onClick = {}, verified = true)
    }
}
