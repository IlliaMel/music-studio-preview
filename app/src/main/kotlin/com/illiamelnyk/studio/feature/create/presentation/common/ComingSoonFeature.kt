package com.illiamelnyk.studio.feature.create.presentation.common

import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.ui.icons.StudioIcons

/**
 * Something Create shows but can't do yet. Its sheet says what it will be and how it will work
 * through the API, and asks whether the user wants it.
 *
 * @param steps how it will work, one line each; parts in backticks are API names.
 */
enum class ComingSoonFeature(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    @param:ArrayRes val steps: Int,
    @param:DrawableRes val icon: Int,
) {
    Muse(
        R.string.create_soon_muse,
        R.string.create_soon_muse_text,
        R.array.create_soon_muse_steps,
        StudioIcons.Forum,
    ),
    Sounds(
        R.string.create_soon_sounds,
        R.string.create_soon_sounds_text,
        R.array.create_soon_sounds_steps,
        StudioIcons.MusicNote,
    ),
    Record(
        R.string.create_soon_record,
        R.string.create_soon_record_text,
        R.array.create_soon_record_steps,
        StudioIcons.Mic,
    ),
    Upload(
        R.string.create_soon_upload,
        R.string.create_soon_upload_text,
        R.array.create_soon_upload_steps,
        StudioIcons.Add,
    ),
    Enhance(
        R.string.create_soon_enhance,
        R.string.create_soon_enhance_text,
        R.array.create_soon_enhance_steps,
        StudioIcons.AutoAwesome,
    ),
    Lyrics(
        R.string.create_soon_lyrics,
        R.string.create_soon_lyrics_text,
        R.array.create_soon_lyrics_steps,
        StudioIcons.Notes,
    ),
    Publish(
        R.string.create_soon_publish,
        R.string.create_soon_publish_text,
        R.array.create_soon_publish_steps,
        StudioIcons.IosShare,
    ),
    AddToPlaylist(
        R.string.create_soon_playlist,
        R.string.create_soon_playlist_text,
        R.array.create_soon_playlist_steps,
        StudioIcons.PlaylistAdd,
    ),
    Download(
        R.string.create_soon_download,
        R.string.create_soon_download_text,
        R.array.create_soon_download_steps,
        StudioIcons.Download,
    ),
    Voice(
        R.string.create_soon_voice,
        R.string.create_soon_voice_text,
        R.array.create_soon_voice_steps,
        StudioIcons.Mic,
    ),
    Sample(
        R.string.create_soon_sample,
        R.string.create_soon_sample_text,
        R.array.create_soon_sample_steps,
        StudioIcons.Mic,
    ),
}
