package com.illiamelnyk.studio.catalog

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.illiamelnyk.studio.core.ui.components.Composer
import com.illiamelnyk.studio.core.ui.components.DurationPicker
import com.illiamelnyk.studio.core.ui.components.PromptPart
import com.illiamelnyk.studio.core.ui.components.PromptTemplate
import com.illiamelnyk.studio.core.ui.components.StudioCheckbox
import com.illiamelnyk.studio.core.ui.components.StudioChip
import com.illiamelnyk.studio.core.ui.components.StudioIconButton
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonSize
import com.illiamelnyk.studio.core.ui.components.StudioIconButtonStyle
import com.illiamelnyk.studio.core.ui.components.StudioSearchField
import com.illiamelnyk.studio.core.ui.components.StudioSegmentedControl
import com.illiamelnyk.studio.core.ui.components.StudioTextField
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * Text fields, choices and the composer. The composer is pinned under the list and the page pads
 * itself for the keyboard and the navigation bar, as a screen with a composer would.
 */
@Composable
internal fun InputsPage() {
    val dimens = StudioTheme.dimens
    var prompt by rememberSaveable { mutableStateOf("") }
    val composerInteraction = remember { MutableInteractionSource() }
    val composerFocused by composerInteraction.collectIsFocusedAsState()
    Column(
        Modifier
            .fillMaxSize()
            // The keyboard or the navigation bar, whichever is taller; applied once, here.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = dimens.spacing.m),
            verticalArrangement = Arrangement.spacedBy(dimens.layout.sectionGap),
        ) {
            item { Section("TextField · SearchField") { TextFields() } }
            item { Section("Checkbox · DurationPicker") { Choices() } }
            item { Section("PromptTemplate") { Template() } }
        }
        Composer(
            value = prompt,
            onValueChange = { prompt = it },
            onSend = { prompt = "" },
            expanded = composerFocused || prompt.isNotEmpty(),
            placeholder = "Describe your track…",
            modifier = Modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.s),
            characterLimit = COMPOSER_LIMIT,
            interactionSource = composerInteraction,
            options = { ComposerOptions() },
            leadingActions = {
                StudioIconButton(
                    StudioIcons.Add,
                    "Attach a reference",
                    onClick = {},
                    style = StudioIconButtonStyle.Surface,
                    size = StudioIconButtonSize.Small,
                )
            },
            trailingActions = {
                StudioIconButton(
                    StudioIcons.AutoAwesome,
                    "Enhance the prompt",
                    onClick = {},
                    style = StudioIconButtonStyle.Surface,
                    size = StudioIconButtonSize.Small,
                )
                StudioIconButton(
                    StudioIcons.Notes,
                    "Lyrics",
                    onClick = {},
                    style = StudioIconButtonStyle.Surface,
                    size = StudioIconButtonSize.Small,
                )
            },
        )
    }
}

@Composable
private fun ComposerOptions() {
    var quality by remember { mutableStateOf("Auto") }
    Row(horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs)) {
        StudioSegmentedControl(listOf("Auto", "Deep", "Fast"), selected = quality, onSelect = {
            quality = it
        }, compact = true) {
            it
        }
        StudioChip("Styles", onClick = {}, leadingIcon = StudioIcons.AutoAwesome)
        StudioChip("2:00", onClick = {}, leadingIcon = StudioIcons.Schedule)
    }
}

@Composable
private fun ColumnScope.TextFields() {
    var email by rememberSaveable { mutableStateOf("") }
    var wrongEmail by rememberSaveable { mutableStateOf("illia@") }
    var query by rememberSaveable { mutableStateOf("") }
    StudioTextField(
        value = email,
        onValueChange = { email = it },
        placeholder = "Enter your email address",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    StudioTextField(
        value = wrongEmail,
        onValueChange = { wrongEmail = it },
        errorText = if ("." in wrongEmail.substringAfter('@')) null else "Enter a full email address",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
    )
    StudioTextField(value = "Read only", onValueChange = {}, enabled = false)
    StudioSearchField(query, onQueryChange = { query = it }, onSearch = {}, placeholder = "Search sounds")
}

@Composable
private fun ColumnScope.Choices() {
    var adult by rememberSaveable { mutableStateOf(true) }
    var updates by rememberSaveable { mutableStateOf(false) }
    var seconds by rememberSaveable { mutableIntStateOf(60) }
    StudioCheckbox(adult, onCheckedChange = { adult = it }, label = "I confirm that I am 18 years of age or older.")
    StudioCheckbox(updates, onCheckedChange = { updates = it }, label = "I want to receive updates.")
    DurationPicker(listOf(30, 45, 60, 90, 180), selected = seconds, onSelect = { seconds = it }) {
        "%d:%02d".format(it / 60, it % 60)
    }
}

@Composable
private fun ColumnScope.Template() {
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    PromptTemplate(
        kind = "New track",
        name = "Genreshift",
        imageSeed = "Genreshift",
        parts = listOf(
            PromptPart.Text("Keep the user's voice and melody, but completely transform the genre into "),
            PromptPart.Slot("genre", "New genre", value = genre),
            PromptPart.Text(". Use the recording as "),
            PromptPart.Slot("reference", "reference"),
            PromptPart.Text(" at medium strength."),
        ),
        onSlotClick = { slot -> if (slot == "genre") genre = if (genre == null) "Synthwave" else null },
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val COMPOSER_LIMIT = 2_000
