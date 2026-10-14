package com.illiamelnyk.studio.feature.player.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.presentation.UiText
import com.illiamelnyk.studio.core.presentation.asString
import com.illiamelnyk.studio.core.ui.components.StudioToast
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.player.presentation.PlayerPreviewSurface
import com.illiamelnyk.studio.feature.player.presentation.PlayerToastUi
import kotlinx.coroutines.delay

/**
 * The kit's toast at the top of the player: it drops in for [toast], stays for [TOAST_MILLIS] and
 * asks to go with [onDismiss]. The app has no toast host yet, so the player shows its own.
 */
@Composable
fun PlayerToast(toast: PlayerToastUi?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val motion = StudioTheme.motion
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // The last toast stays on screen while it leaves.
    val lastShown = remember { LastToast() }
    SideEffect { if (toast != null) lastShown.toast = toast }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        delay(TOAST_MILLIS)
        currentOnDismiss()
    }
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = slideInVertically(tween(motion.duration.medium, easing = motion.easing.emphasized)) { -it } +
            fadeIn(tween(motion.duration.medium)),
        exit = slideOutVertically(tween(motion.duration.medium, easing = motion.easing.standard)) { -it } +
            fadeOut(tween(motion.duration.short)),
    ) {
        val shown = toast ?: lastShown.toast ?: return@AnimatedVisibility
        StudioToast(
            title = shown.title.asString(),
            message = shown.message.asString(),
            modifier = Modifier.padding(StudioTheme.dimens.layout.screenPadding),
            kind = shown.kind,
        )
    }
}

private class LastToast(var toast: PlayerToastUi? = null)

private const val TOAST_MILLIS = 3_000L

@PreviewLightDark
@Composable
private fun PlayerToastPreview() {
    PlayerPreviewSurface {
        PlayerToast(
            toast = PlayerToastUi(UiText.Raw("Thanks, noted"), UiText.Raw("We'll let you know when it's here.")),
            onDismiss = {},
        )
    }
}
