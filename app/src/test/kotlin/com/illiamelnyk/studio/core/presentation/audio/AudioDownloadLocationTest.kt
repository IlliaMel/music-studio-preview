package com.illiamelnyk.studio.core.presentation.audio

import com.illiamelnyk.studio.R
import com.illiamelnyk.studio.core.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDownloadLocationTest {
    @Test
    fun legacyDownloadsDoNotClaimPublicDownloadFolder() {
        assertEquals(UiText.Resource(R.string.player_download_location_legacy), audioDownloadLocation(26))
        assertEquals(UiText.Resource(R.string.player_download_location_legacy), audioDownloadLocation(28))
        assertEquals(UiText.Resource(R.string.player_download_location), audioDownloadLocation(29))
        assertEquals(UiText.Resource(R.string.player_download_location), audioDownloadLocation(33))
    }
}
