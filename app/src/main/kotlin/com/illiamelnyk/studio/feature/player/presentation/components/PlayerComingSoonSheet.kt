package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.ComingSoonSheetContent
import com.illiamelnyk.studio.core.ui.components.StudioBottomSheet
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import com.illiamelnyk.studio.feature.player.presentation.SoonFeature

/**
 * The kit's coming-soon sheet for [feature]: what it will do and how, through the API, and "I want
 * this". `null` closes the sheet; it keeps showing the last feature while it slides away.
 */
@Composable
fun PlayerComingSoonSheet(
    feature: SoonFeature?,
    onWantIt: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastShown = remember { LastShown() }
    SideEffect { if (feature != null) lastShown.feature = feature }
    StudioBottomSheet(visible = feature != null, onDismissRequest = onDismissRequest, modifier = modifier) {
        val content = (feature ?: lastShown.feature)?.content() ?: return@StudioBottomSheet
        ComingSoonSheetContent(
            title = stringResource(content.title),
            description = stringResource(content.description),
            steps = stringArrayResource(content.steps).toList(),
            actionLabel = stringResource(R.string.player_want_it),
            onAction = onWantIt,
            icon = content.icon,
        )
    }
}

/** The feature the sheet showed last, for while it slides away; nothing draws from it directly. */
private class LastShown(var feature: SoonFeature? = null)

private class SoonContent(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:ArrayRes val steps: Int,
    @param:DrawableRes val icon: Int,
)

private fun SoonFeature.content(): SoonContent = when (this) {
    SoonFeature.Remix -> SoonContent(
        R.string.player_remix,
        R.string.player_soon_remix,
        R.array.player_soon_remix_steps,
        StudioIcons.AutoFixHigh,
    )

    SoonFeature.AddToPlaylist -> SoonContent(
        R.string.player_add_to_playlist,
        R.string.player_soon_add_to_playlist,
        R.array.player_soon_add_to_playlist_steps,
        StudioIcons.PlaylistAdd,
    )

    SoonFeature.Autoplay -> SoonContent(
        R.string.player_autoplay,
        R.string.player_soon_autoplay,
        R.array.player_soon_autoplay_steps,
        StudioIcons.Repeat,
    )

    SoonFeature.Download -> SoonContent(
        R.string.player_download,
        R.string.player_soon_download,
        R.array.player_soon_download_steps,
        StudioIcons.Download,
    )
}

@PreviewLightDark
@Composable
private fun PlayerComingSoonSheetPreview() {
    PlayerPreviewSurface {
        Box(Modifier.fillMaxSize()) {
            PlayerComingSoonSheet(feature = SoonFeature.Remix, onWantIt = {}, onDismissRequest = {})
        }
    }
}
