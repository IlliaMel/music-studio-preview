package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioButtonStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.CreateIntent
import com.illiamelnyk.studio.feature.create.presentation.CreateSheet
import com.illiamelnyk.studio.feature.create.presentation.common.OverlaySheet
import com.illiamelnyk.studio.feature.create.presentation.common.rememberLatest

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun GenerationSheets(sheet: CreateSheet?, onIntent: (CreateIntent) -> Unit) {
    OverlaySheet(sheet == CreateSheet.Notify, { onIntent(CreateIntent.NotificationAnswered(false)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.m)) {
            StudioText(stringResource(R.string.create_notify_title), StudioTheme.typography.titleMedium)
            StudioText(
                stringResource(R.string.create_notify_message),
                StudioTheme.typography.bodyMedium,
                color = StudioTheme.colors.content.secondary,
            )
            StudioButton(
                stringResource(R.string.create_notify_yes),
                { onIntent(CreateIntent.NotificationAnswered(true)) },
                modifier = Modifier.fillMaxWidth(),
            )
            StudioButton(
                stringResource(R.string.create_notify_no),
                { onIntent(CreateIntent.NotificationAnswered(false)) },
                modifier = Modifier.fillMaxWidth(),
                style = StudioButtonStyle.Secondary,
            )
        }
    }
    val quota = rememberLatest(sheet as? CreateSheet.Quota)
    OverlaySheet(sheet is CreateSheet.Quota, { onIntent(CreateIntent.SheetDismissed) }) {
        Column(verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.m)) {
            StudioText(
                pluralStringResource(R.plurals.create_quota_title, quota?.dailyLimit ?: 0, quota?.dailyLimit ?: 0),
                StudioTheme.typography.titleMedium,
            )
            StudioButton(stringResource(R.string.create_quota_close), { onIntent(CreateIntent.SheetDismissed) })
        }
    }
}
