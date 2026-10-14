package com.illiamelnyk.studio.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

/**
 * What a [StudioBottomSheet] shows for a feature that isn't there yet: a hero of generated art with
 * the feature's icon, the [title] with a COMING SOON badge, a [description], the numbered [steps] of
 * how it would work through the API, and a button to ask for it.
 *
 * @param steps one line each; a part in backticks, such as `` `upload` ``, is shown as code.
 */
@Composable
fun ComingSoonSheetContent(
    title: String,
    description: String,
    steps: List<String>,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = StudioIcons.AutoAwesome,
    heroSeed: String = title,
) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val typography = StudioTheme.typography
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacing.s)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.size.cover.l)
                .clip(StudioTheme.shapes.l)
                .coverArt(heroSeed),
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(
                icon,
                contentDescription = null,
                tint = colors.overlay.onMediaPrimary,
                size = dimens.size.icon.xl,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioText(title, typography.titleMedium, Modifier.weight(1f, fill = false))
            StudioBadge(stringResource(R.string.ui_coming_soon), style = StudioBadgeStyle.Soon)
        }
        StudioText(description, typography.bodyMedium, color = colors.content.secondary)
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
            val codeStyle = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = typography.bodySmall.fontSize,
                color = colors.content.primary,
                background = colors.surface.level3,
            )
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacing.xs)) {
                    StudioText("${index + 1}.", typography.bodyMedium, color = colors.content.tertiary)
                    BasicText(
                        text = remember(step, codeStyle) { codeSpans(step, codeStyle) },
                        style = typography.bodyMedium.merge(TextStyle(color = colors.content.secondary)),
                    )
                }
            }
        }
        StudioButton(
            text = actionLabel,
            onClick = onAction,
            modifier = Modifier.fillMaxWidth(),
            size = StudioButtonSize.Large,
        )
    }
}

/** The kit's `.s-code`: parts in backticks in a monospaced face on `surface.level3`. */
private fun codeSpans(text: String, codeStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    text.split('`').forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(codeStyle) { append(part) } else append(part)
    }
}

@PreviewLightDark
@Composable
private fun ComingSoonSheetContentPreview() {
    PreviewSurface {
        ComingSoonSheetContent(
            title = "Voice to Song",
            description = "Hum or sing a melody and get a full track around it.",
            steps = listOf("Record up to 30 s", "`upload` the recording", "Pass it as `conditioning_ref`"),
            actionLabel = "I want this",
            onAction = {},
            icon = StudioIcons.Mic,
        )
    }
}
