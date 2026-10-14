package com.illiamelnyk.studio.feature.create.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.presentation.preview.PreviewScreen
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.studioIndication
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.StudioTheme
import com.illiamelnyk.studio.feature.create.presentation.ModelUi
import com.illiamelnyk.studio.feature.create.presentation.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** The music models side by side as cards, one of them selected; TalkBack reads them as radio buttons. */
@Composable
fun ModelCards(
    models: ImmutableList<ModelUi>,
    selected: MusicModel,
    onSelect: (MusicModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // The cards are as tall as the taller one.
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.layout.itemGap),
    ) {
        models.forEach { model ->
            ModelCard(
                model = model,
                selected = model.model == selected,
                onClick = { onSelect(model.model) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Suppress("ktlint:compose:modifier-clickable-order") // The press scale has to shrink the clipped card.
@Composable
private fun ModelCard(model: ModelUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val shape = StudioTheme.shapes.m
    val outline by animateColorAsState(
        targetValue = if (selected) colors.inverse.surface else colors.outline.subtle,
        animationSpec = tween(StudioTheme.motion.duration.short, easing = StudioTheme.motion.easing.standard),
        label = "modelOutline",
    )
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = studioIndication(shape),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .clip(shape)
            .background(colors.surface.level3)
            .border(dimens.stroke.thick, outline, shape)
            .padding(dimens.layout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudioText(stringResource(model.name), StudioTheme.typography.titleSmall, Modifier.weight(1f), maxLines = 1)
            if (selected) {
                StudioIcon(
                    StudioIcons.CheckCircleFilled,
                    contentDescription = null,
                    size = dimens.size.icon.m,
                )
            }
        }
        StudioText(
            stringResource(model.description),
            StudioTheme.typography.bodySmall,
            color = colors.content.secondary,
        )
    }
}

@PreviewLightDark
@Composable
private fun ModelCardsPreview() {
    PreviewScreen {
        ModelCards(
            models = MusicModel.entries.map(MusicModel::toUi).toImmutableList(),
            selected = MusicModel.V2_5,
            onSelect = {},
            modifier = Modifier.padding(StudioTheme.dimens.spacing.m),
        )
    }
}
