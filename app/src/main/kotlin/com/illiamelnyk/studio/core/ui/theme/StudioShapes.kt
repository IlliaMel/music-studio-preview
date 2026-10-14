package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii from `radius` in tokens.json, with where each one is used. */
@Immutable
data class StudioShapes(
    /** Full-screen elements. */
    val none: Shape = RectangleShape,
    /** Covers of 40 dp and smaller. */
    val xs: RoundedCornerShape = RoundedCornerShape(4.dp),
    /** Covers of 48 to 56 dp, text fields. */
    val s: RoundedCornerShape = RoundedCornerShape(8.dp),
    /** Tool tiles, the 144 dp cover. */
    val m: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** Toasts, menus, station cards, the cover in the player. */
    val l: RoundedCornerShape = RoundedCornerShape(16.dp),
    /** Large cards. */
    val xl: RoundedCornerShape = RoundedCornerShape(24.dp),
    /** Sheets and dialogs. */
    val xxl: RoundedCornerShape = RoundedCornerShape(28.dp),
    /** Pills and circles: buttons, chips, segments, the composer, the mini player, avatars. */
    val full: RoundedCornerShape = CircleShape,
)
