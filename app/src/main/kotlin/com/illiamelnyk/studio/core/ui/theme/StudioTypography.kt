package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.illiamelnyk.studio.R

/**
 * The shared Inter type scale, including headings and action labels measured from the references; sizes and line
 * heights in sp so text follows the system font size. Every text in the app uses exactly one of them.
 */
@Immutable
data class StudioTypography(
    /** Tab title. */
    val headlineLarge: TextStyle = interStyle(size = 32.sp, lineHeight = 40.sp, FontWeight.Bold, tracking = (-0.5).sp),
    /** Player and collection title, lyrics. */
    val titleLarge: TextStyle = interStyle(size = 22.sp, lineHeight = 28.sp, FontWeight.SemiBold),
    val sectionTitle: TextStyle = interStyle(size = 20.sp, lineHeight = 28.sp, FontWeight.SemiBold),
    /** Toolbar and sheet title. */
    val titleMedium: TextStyle = interStyle(size = 16.sp, lineHeight = 24.sp, FontWeight.SemiBold),
    /** Row title. */
    val titleSmall: TextStyle = interStyle(size = 14.sp, lineHeight = 20.sp, FontWeight.Medium),
    /** Prompt input, long text. */
    val bodyLarge: TextStyle = interStyle(size = 16.sp, lineHeight = 24.sp, FontWeight.Normal),
    /** Body text, a setting's value. */
    val bodyMedium: TextStyle = interStyle(size = 14.sp, lineHeight = 20.sp, FontWeight.Normal),
    /** Artist, metadata, captions. */
    val bodySmall: TextStyle = interStyle(size = 12.sp, lineHeight = 16.sp, FontWeight.Normal),
    val labelExtraLarge: TextStyle = interStyle(size = 16.sp, lineHeight = 24.sp, FontWeight.Medium),
    /** Buttons, chips, segments. */
    val labelLarge: TextStyle = interStyle(size = 14.sp, lineHeight = 20.sp, FontWeight.Medium),
    /** Navigation labels, small buttons. */
    val labelMedium: TextStyle = interStyle(size = 12.sp, lineHeight = 16.sp, FontWeight.Medium),
    /** Badges. */
    val labelSmall: TextStyle = interStyle(size = 11.sp, lineHeight = 16.sp, FontWeight.Bold, tracking = 0.3.sp),
)

/**
 * Inter from Google Fonts (SIL Open Font License 1.1), bundled as one variable font so it works
 * without Google Play services. Each weight of `typography.weights` pins the font's weight axis.
 */
private val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.inter,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun interStyle(size: TextUnit, lineHeight: TextUnit, weight: FontWeight, tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    // The line box is exactly lineHeight with the glyphs centered in it, as in the CSS of the kit.
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
