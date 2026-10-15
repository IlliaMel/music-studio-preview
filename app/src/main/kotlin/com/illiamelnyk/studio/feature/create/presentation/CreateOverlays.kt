package com.illiamelnyk.studio.feature.create.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.StudioConfirmDialog
import com.illiamelnyk.studio.feature.create.presentation.common.ComingSoonSheet
import com.illiamelnyk.studio.feature.create.presentation.components.ComposerSheets
import com.illiamelnyk.studio.feature.create.presentation.components.GenerationSheets
import com.illiamelnyk.studio.feature.create.presentation.components.SettingsCallbacks
import com.illiamelnyk.studio.feature.create.presentation.components.SettingsSheet

/**
 * Part of [CreateScreen]: the sheets and the delete dialog over the screen, which map their
 * callbacks to intents. Each sheet is in its own window, over the shell's bars.
 */
// The sheets and the dialog are windows of their own; nothing here is laid out in the screen.
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun CreateOverlays(state: CreateState, onIntent: (CreateIntent) -> Unit) {
    val dismiss = { onIntent(CreateIntent.SheetDismissed) }
    ComposerSheets(state.sheet, state.composer.lyricsMode, onIntent)
    GenerationSheets(state.sheet, onIntent)
    SettingsSheet(
        draft = (state.sheet as? CreateSheet.Settings)?.draft,
        callbacks = remember(onIntent) {
            SettingsCallbacks(
                onQualitySelect = { onIntent(CreateIntent.SettingsQualitySelected(it)) },
                onVariantsSelect = { onIntent(CreateIntent.SettingsVariantsSelected(it)) },
                onInstrumentalChange = { onIntent(CreateIntent.SettingsInstrumentalChanged(it)) },
                onCustomLyrics = { onIntent(CreateIntent.SettingsCustomLyricsSelected) },
                onDurationSelect = { onIntent(CreateIntent.SettingsDurationSelected(it)) },
                onReset = { onIntent(CreateIntent.SettingsReset) },
                onApply = { onIntent(CreateIntent.SettingsApplied) },
                onComingSoon = { onIntent(CreateIntent.ComingSoonClicked(it)) },
            )
        },
        onDismissRequest = dismiss,
    )
    ComingSoonSheet(
        feature = (state.sheet as? CreateSheet.ComingSoon)?.feature,
        onWantClick = { onIntent(CreateIntent.WantClicked) },
        onDismissRequest = dismiss,
    )
    state.deleting?.let { track ->
        StudioConfirmDialog(
            title = stringResource(R.string.create_delete_title, track.title),
            message = stringResource(R.string.create_delete_message),
            confirmLabel = stringResource(R.string.create_menu_delete),
            dismissLabel = stringResource(R.string.create_delete_cancel),
            onConfirm = { onIntent(CreateIntent.DeleteConfirmed) },
            onDismissRequest = { onIntent(CreateIntent.DeleteDismissed) },
        )
    }
}
