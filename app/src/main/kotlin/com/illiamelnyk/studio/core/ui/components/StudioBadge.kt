package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.icons.StudioIcon
import com.illiamelnyk.studio.core.ui.icons.StudioIcons
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

enum class StudioBadgeStyle {
    /** A model version or a kind of track: "V2.5", "REMIX". */
    Tag,

    /** Something the user hasn't seen yet. */
    New,

    /** A feature that isn't available yet. Looks like [Tag]; the style says what it means. */
    Soon,

    /** Data that comes from the mock backend, not from the real API. */
    Demo,
}

/** A 20 dp label in capitals next to a title or inside a row. */
@Composable
fun StudioBadge(text: String, modifier: Modifier = Modifier, style: StudioBadgeStyle = StudioBadgeStyle.Tag) {
    val colors = StudioTheme.colors
    val (container, content) = when (style) {
        StudioBadgeStyle.Tag, StudioBadgeStyle.Soon -> colors.surface.level4 to colors.content.secondary
        StudioBadgeStyle.New -> colors.accent.new to colors.accent.onNew
        StudioBadgeStyle.Demo -> colors.accent.demo to colors.accent.onDemo
    }
    Box(
        modifier = modifier
            .heightIn(min = StudioTheme.dimens.size.component.badge)
            .background(container, StudioTheme.shapes.full)
            .padding(horizontal = StudioTheme.dimens.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        StudioText(
            text = remember(text) { text.toUpperCase(Locale.current) },
            style = StudioTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
        )
    }
}

/** The blue check next to the name of a verified artist, 16 dp. */
@Composable
fun VerifiedMark(modifier: Modifier = Modifier) {
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    val description = stringResource(R.string.ui_verified)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .size(dimens.size.icon.s),
    ) {
        drawCircle(colors.accent.verified)
        val check = Path().apply {
            moveTo(size.width * CHECK_START.x, size.height * CHECK_START.y)
            lineTo(size.width * CHECK_CORNER.x, size.height * CHECK_CORNER.y)
            lineTo(size.width * CHECK_END.x, size.height * CHECK_END.y)
        }
        drawPath(check, colors.accent.onVerified, style = Stroke(width = dimens.stroke.thick.toPx()))
    }
}

// The kit draws the check as an L of 6 × 3 px with 2 px strokes, rotated by -45° in the 16 px
// circle. These are the ends and the corner of that L as fractions of the mark's size.
private val CHECK_START = Offset(0.26f, 0.43f)
private val CHECK_CORNER = Offset(0.43f, 0.61f)
private val CHECK_END = Offset(0.74f, 0.3f)

/** How many generations the user has left: a note and the number. TalkBack reads "25 credits left". */
@Composable
fun CreditsCounter(left: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.ui_credits_left, left, left)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioIcon(StudioIcons.MusicNote, contentDescription = null, size = StudioTheme.dimens.size.icon.s)
        StudioText(left.toString(), StudioTheme.typography.labelLarge)
    }
}

@PreviewLightDark
@Composable
private fun StudioBadgePreview() {
    PreviewSurface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StudioTheme.dimens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerifiedMark()
            StudioBadge("v2.5")
            StudioBadge("Remix")
            StudioBadge("New", style = StudioBadgeStyle.New)
            StudioBadge("Soon", style = StudioBadgeStyle.Soon)
            StudioBadge("Demo", style = StudioBadgeStyle.Demo)
            CreditsCounter(left = 25)
        }
    }
}

@Composable
fun StudioSoonBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.background(StudioTheme.colors.surface.level4, StudioTheme.shapes.full)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        StudioText(
            stringResource(R.string.ui_soon),
            StudioTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
            color = StudioTheme.colors.content.secondary,
            maxLines = 1,
        )
    }
}
