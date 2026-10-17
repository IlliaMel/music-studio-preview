package com.illiamelnyk.studio.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import com.illiamelnyk.studio.core.ui.foundation.StudioText
import com.illiamelnyk.studio.core.ui.foundation.skeleton

@Composable
fun SkeletonTextLine(style: TextStyle, modifier: Modifier = Modifier) {
    StudioText(" ", style, modifier.skeleton(true).clearAndSetSemantics {}, maxLines = 1)
}
