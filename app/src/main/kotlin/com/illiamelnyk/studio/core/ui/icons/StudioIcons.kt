package com.illiamelnyk.studio.core.ui.icons

import androidx.annotation.DrawableRes
import com.illiamelnyk.studio.R

/**
 * Icons of the design system, drawn with [StudioIcon].
 *
 * They are Material Symbols Rounded by Google (Apache License 2.0,
 * https://github.com/google/material-design-icons), at optical size 24: outlined icons at weight
 * 300 and `*Filled` icons at fill 1, weight 400, as in ui-kit.html. The SVG paths are converted to
 * vector drawables unchanged, with a white fill that [StudioIcon] tints. Playback icons keep their
 * direction in right-to-left layouts, as media controls do.
 */
object StudioIcons {
    @DrawableRes val Add = R.drawable.ic_add

    @DrawableRes val ArrowUpward = R.drawable.ic_arrow_upward

    @DrawableRes val AutoAwesome = R.drawable.ic_auto_awesome

    @DrawableRes val AutoAwesomeFilled = R.drawable.ic_auto_awesome_filled

    @DrawableRes val AutoFixHigh = R.drawable.ic_auto_fix_high

    @DrawableRes val CheckCircleFilled = R.drawable.ic_check_circle_filled

    /** Mirrored in right-to-left layouts, like every "back" arrow. */
    @DrawableRes val ChevronLeft = R.drawable.ic_chevron_left

    /** Mirrored in right-to-left layouts. */
    @DrawableRes val ChevronRight = R.drawable.ic_chevron_right

    @DrawableRes val Close = R.drawable.ic_close

    @DrawableRes val Delete = R.drawable.ic_delete

    @DrawableRes val Download = R.drawable.ic_download

    @DrawableRes val Edit = R.drawable.ic_edit

    @DrawableRes val Error = R.drawable.ic_error

    @DrawableRes val ErrorFilled = R.drawable.ic_error_filled

    @DrawableRes val FastForwardFilled = R.drawable.ic_fast_forward_filled

    @DrawableRes val Favorite = R.drawable.ic_favorite

    @DrawableRes val FavoriteFilled = R.drawable.ic_favorite_filled

    @DrawableRes val FilterNone = R.drawable.ic_filter_none

    @DrawableRes val Forum = R.drawable.ic_forum

    @DrawableRes val Headphones = R.drawable.ic_headphones

    @DrawableRes val HeadphonesFilled = R.drawable.ic_headphones_filled

    @DrawableRes val IosShare = R.drawable.ic_ios_share

    @DrawableRes val LibraryMusic = R.drawable.ic_library_music

    @DrawableRes val LibraryMusicFilled = R.drawable.ic_library_music_filled

    @DrawableRes val Mic = R.drawable.ic_mic

    @DrawableRes val MoreHoriz = R.drawable.ic_more_horiz

    @DrawableRes val MusicNote = R.drawable.ic_music_note

    @DrawableRes val Notes = R.drawable.ic_notes

    @DrawableRes val PauseFilled = R.drawable.ic_pause_filled

    @DrawableRes val PlayArrowFilled = R.drawable.ic_play_arrow_filled

    @DrawableRes val PlaylistAdd = R.drawable.ic_playlist_add

    @DrawableRes val PlaylistPlay = R.drawable.ic_playlist_play

    @DrawableRes val Refresh = R.drawable.ic_refresh

    @DrawableRes val Repeat = R.drawable.ic_repeat

    @DrawableRes val Schedule = R.drawable.ic_schedule

    @DrawableRes val Search = R.drawable.ic_search

    @DrawableRes val Shuffle = R.drawable.ic_shuffle

    @DrawableRes val SkipNextFilled = R.drawable.ic_skip_next_filled

    @DrawableRes val SkipPreviousFilled = R.drawable.ic_skip_previous_filled

    @DrawableRes val StopFilled = R.drawable.ic_stop_filled

    @DrawableRes val Tune = R.drawable.ic_tune
}
