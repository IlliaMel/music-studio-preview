package com.illiamelnyk.studio.core.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.LocalWindowFeedback
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppFeedbackHost(
    modifier: Modifier = Modifier,
    viewModel: OfflineViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val online by viewModel.isOnline.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(StudioTheme.colors.background)) {
        if (!online) OfflineBanner(onRetry = viewModel::retry)
        Box(Modifier.weight(1f)) { content() }
    }
    LocalWindowFeedback.current(0)
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.statusBarsPadding().padding(StudioTheme.dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.s),
    ) {
        Column(Modifier.weight(1f)) {
            StudioText(stringResource(R.string.network_offline), StudioTheme.typography.titleSmall)
            StudioText(stringResource(R.string.network_offline_detail), StudioTheme.typography.bodySmall)
        }
        StudioButton(stringResource(R.string.network_retry), onRetry, style = StudioButtonStyle.Text)
    }
}
