package com.illiamelnyk.studio.core.ui.foundation

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.illiamelnyk.studio.core.ui.theme.missingTheme

/**
 * Color of text and icons that don't set one: StudioTheme provides `content.primary`, and a
 * component that draws its own container (a primary button, a selected chip) provides the matching
 * `on` color for everything inside it.
 *
 * Unlike the theme locals it isn't static: it changes often, sometimes on every frame of a color
 * animation, and only the text and icons that read it should redraw then.
 */
val LocalStudioContentColor = compositionLocalOf<Color> { missingTheme() }

/** Makes [color] the default color of text and icons in [content]. */
@Composable
fun ProvideContentColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStudioContentColor provides color, content = content)
}

/**
 * Text in one of the theme's type styles (`StudioTheme.typography`), drawn with [BasicText] in the
 * current content color unless [color] is given.
 */
@Composable
fun StudioText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalStudioContentColor.current,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign == TextAlign.Unspecified) style else style.copy(textAlign = textAlign),
        overflow = overflow,
        maxLines = maxLines,
        color = { color },
    )
}
