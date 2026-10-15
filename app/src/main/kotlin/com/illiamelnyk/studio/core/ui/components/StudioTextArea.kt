package com.illiamelnyk.studio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.rememberStudioEditor
import com.illiamelnyk.studio.core.ui.theme.PreviewSurface
import com.illiamelnyk.studio.core.ui.theme.StudioTheme

@Composable
fun StudioTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 4,
    maxLines: Int = 8,
) {
    val editor = rememberStudioEditor(value, onValueChange)
    val colors = StudioTheme.colors
    val dimens = StudioTheme.dimens
    BasicTextField(
        state = editor,
        modifier = modifier.fillMaxWidth(),
        textStyle = StudioTheme.typography.bodyLarge.copy(color = colors.content.primary),
        cursorBrush = SolidColor(colors.content.primary),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines),
        decorator = { field ->
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = dimens.size.component.textField * minLines)
                    .background(colors.surface.level2, StudioTheme.shapes.m)
                    .border(dimens.stroke.hairline, colors.outline.subtle, StudioTheme.shapes.m)
                    .padding(dimens.spacing.m),
            ) {
                if (editor.text.isEmpty()) {
                    StudioText(
                        placeholder,
                        StudioTheme.typography.bodyLarge,
                        color = colors.content.tertiary,
                    )
                }
                field()
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun StudioTextAreaPreview() {
    PreviewSurface { StudioTextArea("", {}, placeholder = "Write your lyrics") }
}
