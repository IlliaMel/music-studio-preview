package com.illiamelnyk.studio.core.ui.icons

import androidx.annotation.DrawableRes
import com.illiamelnyk.studio.R

/**
 * Icons of the design system, drawn with [StudioIcon].
 *
 * They are Material Symbols Rounded by Google (Apache License 2.0,
 * https://github.com/google/material-design-icons), at optical size 24: outlined icons at weight
 * 300 and `*Filled` icons at fill 1, weight 400, as in ui-kit.html. The SVG paths are converted to
 * vector drawables unchanged, with a white fill that [StudioIcon] tints.
 */
object StudioIcons {
    @DrawableRes val Add = R.drawable.ic_add

    @DrawableRes val ArrowUpward = R.drawable.ic_arrow_upward

    @DrawableRes val AutoAwesome = R.drawable.ic_auto_awesome

    @DrawableRes val AutoAwesomeFilled = R.drawable.ic_auto_awesome_filled

    @DrawableRes val CheckCircleFilled = R.drawable.ic_check_circle_filled

    /** Mirrored in right-to-left layouts, like every "back" arrow. */
    @DrawableRes val ChevronLeft = R.drawable.ic_chevron_left

    @DrawableRes val Close = R.drawable.ic_close

    @DrawableRes val Edit = R.drawable.ic_edit

    @DrawableRes val Error = R.drawable.ic_error

    @DrawableRes val ErrorFilled = R.drawable.ic_error_filled

    @DrawableRes val Forum = R.drawable.ic_forum

    @DrawableRes val Headphones = R.drawable.ic_headphones

    @DrawableRes val HeadphonesFilled = R.drawable.ic_headphones_filled

    @DrawableRes val IosShare = R.drawable.ic_ios_share

    @DrawableRes val LibraryMusic = R.drawable.ic_library_music

    @DrawableRes val LibraryMusicFilled = R.drawable.ic_library_music_filled

    @DrawableRes val MoreHoriz = R.drawable.ic_more_horiz

    @DrawableRes val MusicNote = R.drawable.ic_music_note

    @DrawableRes val Refresh = R.drawable.ic_refresh

    @DrawableRes val Search = R.drawable.ic_search

    @DrawableRes val Shuffle = R.drawable.ic_shuffle

    @DrawableRes val Tune = R.drawable.ic_tune
}
