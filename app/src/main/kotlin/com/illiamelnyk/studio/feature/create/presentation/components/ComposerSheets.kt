package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.components.ContextMenuItem
import com.illiamelnyk.studio.core.ui.components.OverlaySheet
import com.illiamelnyk.studio.core.ui.components.StudioButton
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.domain.LyricsMode
import com.illiamelnyk.studio.feature.create.presentation.CreateIntent
import com.illiamelnyk.studio.feature.create.presentation.CreateSheet
import com.illiamelnyk.studio.feature.create.presentation.common.rememberLatest

/** Composer choices and track naming are separate modal windows above the app shell. */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun ComposerSheets(
    sheet: CreateSheet?,
    lyricsMode: LyricsMode,
    onIntent: (CreateIntent) -> Unit,
    customLyricsSupported: Boolean = true,
) {
    val dismiss = { onIntent(CreateIntent.SheetDismissed) }
    OverlaySheet(visible = sheet == CreateSheet.Lyrics, onDismissRequest = dismiss) {
        Column {
            StudioText(stringResource(R.string.create_composer_lyrics), StudioTheme.typography.titleMedium)
            ContextMenuItem(
                stringResource(R.string.create_settings_instrumental),
                { onIntent(CreateIntent.LyricsSelected(true)) },
                icon = if (lyricsMode == LyricsMode.Instrumental) StudioIcons.CheckCircleFilled else null,
            )
            if (customLyricsSupported) {
                ContextMenuItem(
                    stringResource(R.string.create_lyrics_custom),
                    { onIntent(CreateIntent.CustomLyricsClicked) },
                    icon = if (lyricsMode == LyricsMode.Custom) StudioIcons.CheckCircleFilled else null,
                )
            }
            ContextMenuItem(
                stringResource(R.string.create_quality_auto),
                { onIntent(CreateIntent.LyricsSelected(false)) },
                icon = if (lyricsMode == LyricsMode.Auto) StudioIcons.CheckCircleFilled else null,
            )
        }
    }
    val draft = rememberLatest(sheet as? CreateSheet.Rename)
    OverlaySheet(visible = sheet is CreateSheet.Rename, onDismissRequest = dismiss) {
        Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.m)) {
            StudioText(stringResource(R.string.create_menu_rename), StudioTheme.typography.titleMedium)
            StudioTextField(draft?.title.orEmpty(), { onIntent(CreateIntent.RenameChanged(it)) })
            StudioButton(
                stringResource(R.string.create_rename_save),
                { onIntent(CreateIntent.RenameConfirmed) },
                enabled = !draft?.title.isNullOrBlank(),
            )
        }
    }
}
