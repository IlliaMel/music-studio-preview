package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.components.Cover
import com.illiamelnyk.studio.core.ui.components.EmptyState
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.PromptIdeaUi
import com.illiamelnyk.studio.feature.create.presentation.promptIdeas
import kotlinx.collections.immutable.ImmutableList

/**
 * What "Your tracks" shows before the first track: what Create is for, and [ideas] for a first
 * prompt. Tapping one hands its prompt to [onIdeaClick].
 */
@Composable
fun PromptIdeas(ideas: ImmutableList<PromptIdeaUi>, onIdeaClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    Column(
        modifier = modifier.padding(horizontal = dimens.layout.screenPadding, vertical = dimens.spacing.m),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
    ) {
        EmptyState(
            icon = StudioIcons.AutoAwesome,
            title = stringResource(R.string.create_empty_title),
            message = stringResource(R.string.create_empty_message),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.spacing.m),
        )
        ideas.forEach { idea ->
            val prompt = stringResource(idea.prompt)
            PromptIdeaCard(title = stringResource(idea.title), prompt = prompt, onClick = { onIdeaClick(prompt) })
        }
    }
}

/** An idea as a card: generated art for its title, the title and the prompt it puts in the composer. */
@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped card.
@Composable
private fun PromptIdeaCard(title: String, prompt: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.m
    Row(
        modifier = modifier
            .clickable(
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.Button,
                onClick = onClick,
            )
            .fillMaxWidth()
            .clip(shape)
            .background(StudioTheme.colors.surface.level3)
            .padding(dimens.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(seed = title, size = dimens.size.cover.xs)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.spacing.hairline)) {
            StudioText(title, StudioTheme.typography.titleSmall, maxLines = 1)
            StudioText(
                prompt,
                StudioTheme.typography.bodySmall,
                color = StudioTheme.colors.content.secondary,
                maxLines = 2,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PromptIdeasPreview() {
    PreviewScreen { PromptIdeas(ideas = promptIdeas, onIdeaClick = {}) }
}
