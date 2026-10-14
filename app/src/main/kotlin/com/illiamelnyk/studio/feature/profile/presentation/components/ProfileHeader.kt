package com.illiamelnyk.studio.feature.profile.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.profile.presentation.ProfileUserUi

/** The avatar, the name, the email and the tracks left today. */
@Composable
fun ProfileHeader(user: ProfileUserUi, credits: UiText?, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    val colors = StudioTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(seed = user.avatarSeed, size = dimens.size.avatar.l, shape = StudioTheme.shapes.full)
        StudioText(user.name, StudioTheme.typography.titleLarge, maxLines = 1)
        if (user.email != null) {
            StudioText(user.email, StudioTheme.typography.bodySmall, color = colors.content.secondary)
        }
        if (credits != null) {
            StudioText(credits.asString(), StudioTheme.typography.bodyMedium, color = colors.content.secondary)
        }
    }
}

@PreviewLightDark
@Composable
private fun ProfileHeaderPreview() {
    PreviewScreen {
        ProfileHeader(
            user = ProfileUserUi(name = "Illia Melnyk", email = "illia@dev.local", avatarSeed = "dev-illia"),
            credits = UiText.Plural(R.plurals.profile_credits_left, 8, 7, 8),
        )
    }
}
