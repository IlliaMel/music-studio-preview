package com.illiamelnyk.studio.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, layout and sizes from tokens.json, grouped and named as there: `size.component.trackRow`
 * in the JSON is `dimens.size.component.trackRow` here. Everything sits on the 4 dp grid.
 */
@Immutable
data class StudioDimens(val spacing: Spacing = Spacing(), val layout: Layout = Layout(), val size: Sizes = Sizes()) {
    /** Paddings and gaps. [hairline] is for thin gaps and strokes only. */
    @Immutable
    data class Spacing(
        val none: Dp = 0.dp,
        val hairline: Dp = 2.dp,
        val xxs: Dp = 4.dp,
        val xs: Dp = 8.dp,
        val s: Dp = 12.dp,
        val m: Dp = 16.dp,
        val l: Dp = 20.dp,
        val xl: Dp = 24.dp,
        val xxl: Dp = 32.dp,
        val xxxl: Dp = 40.dp,
        val huge: Dp = 48.dp,
        val giant: Dp = 64.dp,
    )

    /** Spacing with a fixed meaning on every screen. */
    @Immutable
    data class Layout(
        /** Between the screen edge and the content. */
        val screenPadding: Dp = 16.dp,
        /** Between cards in a row or a grid. */
        val itemGap: Dp = 12.dp,
        /** Between sections of a screen. */
        val sectionGap: Dp = 32.dp,
        /** Between an icon and its text, or elements in a row. */
        val inlineGap: Dp = 8.dp,
        /** Inside cards and sheets. */
        val cardPadding: Dp = 16.dp,
    )

    @Immutable
    data class Sizes(
        /** The smallest touch area of an interactive element; smaller elements reserve it around them. */
        val touchTarget: Dp = 48.dp,
        val icon: Icon = Icon(),
        val control: Control = Control(),
        val avatar: Avatar = Avatar(),
        val cover: Cover = Cover(),
        val component: Component = Component(),
    )

    @Immutable
    data class Icon(
        /** In chips and small buttons. */
        val s: Dp = 16.dp,
        /** In buttons. */
        val m: Dp = 20.dp,
        /** The default. */
        val l: Dp = 24.dp,
        /** Empty states. */
        val xl: Dp = 32.dp,
    )

    /** Heights of controls. */
    @Immutable
    data class Control(val s: Dp = 32.dp, val m: Dp = 40.dp, val l: Dp = 48.dp, val xl: Dp = 56.dp)

    @Immutable
    data class Avatar(val xs: Dp = 24.dp, val s: Dp = 32.dp, val m: Dp = 40.dp, val l: Dp = 64.dp, val xl: Dp = 96.dp)

    @Immutable
    data class Cover(
        /** Mini player, menus. */
        val xs: Dp = 40.dp,
        /** Track row. */
        val s: Dp = 48.dp,
        /** Tool tile. */
        val m: Dp = 56.dp,
        /** Carousel card: about 2.5 cards on a 393 dp wide screen. */
        val l: Dp = 144.dp,
    )

    /** Heights (and a few widths) of specific components. */
    @Immutable
    data class Component(
        val badge: Dp = 20.dp,
        val chip: Dp = 32.dp,
        /** A 32 dp segment plus 4 dp padding on each side. */
        val segmentedControl: Dp = 40.dp,
        val segment: Dp = 32.dp,
        val button: Dp = 40.dp,
        val buttonLarge: Dp = 48.dp,
        val iconButton: Dp = 40.dp,
        val iconButtonSmall: Dp = 32.dp,
        val textField: Dp = 48.dp,
        val composerMin: Dp = 48.dp,
        /** A 48 dp cover plus 8 dp above and below: denser than Material's 72 dp two-line item. */
        val trackRow: Dp = 64.dp,
        val listRow: Dp = 56.dp,
        val toolTile: Dp = 56.dp,
        val miniPlayer: Dp = 56.dp,
        val topBar: Dp = 56.dp,
        val navigationBar: Dp = 80.dp,
        val stationWidth: Dp = 320.dp,
        val stationHeight: Dp = 180.dp,
        val sheetHandleWidth: Dp = 32.dp,
        val sheetHandleHeight: Dp = 4.dp,
        val seekTrack: Dp = 4.dp,
        val seekThumb: Dp = 12.dp,
        val playButton: Dp = 64.dp,
        /** Dividers and component borders. */
        val divider: Dp = 1.dp,
    )
}
