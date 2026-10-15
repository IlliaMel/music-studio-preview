package com.illiamelnyk.studio.core.data.playback

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.graphics.Bitmap
import android.media.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import com.illiamelnyk.studio.core.artwork.ArtworkAtlasCache
import com.illiamelnyk.studio.core.domain.playback.PlaybackResumption
import com.illiamelnyk.studio.core.domain.playback.PlaybackState
import com.illiamelnyk.studio.feature.player.fake.track
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemMediaTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun artworkIsSharedBoundedAndMatchesTheUiAtlasTile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = AndroidMediaArtwork(context, dispatcher)
        val first = async { provider.load("Glass Harbor") }
        val second = async { provider.load("glass-harbor") }
        val bitmap = requireNotNull(first.await())
        assertSame(bitmap, second.await())
        assertTrue(bitmap.width <= 320 && bitmap.height <= 320)
        assertEquals(bitmap.width, bitmap.height)
        val atlas = requireNotNull(ArtworkAtlasCache.load(context.assets, dispatcher))
        assertSame(atlas, ArtworkAtlasCache.load(context.assets, dispatcher))
        val tile = atlas.resolve("Glass Harbor")
        val expected = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(tile.image, tile.left, tile.top, tile.side, tile.side),
            320,
            320,
            true,
        )
        assertTrue(expected.sameAs(bitmap))
        assertFalse(provider.load("Night Drive") === bitmap)
    }

    @Test
    fun systemMetadataContainsCoverAndNeverAudioUriOrCredentials() {
        val song = track("private-song", 20.seconds).copy(
            audioUri = "https://private.example/audio",
            audioHeaders = mapOf("Authorization" to "Bearer fixture-secret"),
        )
        val state = PlaybackState(queue = listOf(song), currentIndex = 0, positionMs = 4_000)
        val cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val metadata = mediaMetadata(state, cover)
        assertEquals(song.title, metadata.getString(MediaMetadata.METADATA_KEY_TITLE))
        assertEquals(20_000L, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
        assertSame(cover, metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        val recent = recentMediaItem(PlaybackResumption("opaque-id", state), cover)
        assertEquals("opaque-id", recent.mediaId)
        assertTrue(recent.isPlayable)
        assertFalse(recent.isBrowsable)
        assertSame(cover, recent.description.iconBitmap)
        assertNull(recent.description.mediaUri)
        assertNull(recent.description.extras)
        assertNull(recent.description.iconUri)
        assertEquals(
            setOf(
                MediaMetadata.METADATA_KEY_TITLE,
                MediaMetadata.METADATA_KEY_ARTIST,
                MediaMetadata.METADATA_KEY_DURATION,
                MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON,
            ),
            metadata.keySet(),
        )
        assertEquals(
            -1L,
            mediaMetadata(state.copy(stationName = "Live"), cover)
                .getLong(MediaMetadata.METADATA_KEY_DURATION),
        )
    }

    @Test
    fun browserIsExportedOnlyBehindSystemMediaPermission() {
        val info = context.packageManager.getServiceInfo(ComponentName(context, PlaybackService::class.java), 0)
        assertTrue(info.exported)
        assertEquals("android.permission.MEDIA_CONTENT_CONTROL", info.permission)
    }

    @Test
    fun trustedCallerRequiresAllowedPackageUidAndPlatformSignatureTogether() {
        val packages = shadowOf(context.packageManager)
        fun install(name: String, uid: Int, signature: String) {
            packages.installPackage(
                PackageInfo().apply {
                    packageName = name
                    applicationInfo = ApplicationInfo().apply {
                        packageName = name
                        this.uid = uid
                    }
                    signatures = arrayOf(Signature(signature))
                },
            )
            packages.setPackagesForUid(uid, name)
        }
        install("android", 1000, "0102")
        install("com.android.systemui", 1001, "0102")
        install("untrusted.media", 2001, "0102")
        val trust = TrustedMediaCaller(context)
        assertTrue(trust.accepts("com.android.systemui", 1001))
        assertTrue(trust.accepts("android", 1000))
        assertFalse(trust.accepts("com.android.systemui", 2001))
        assertFalse(trust.accepts("untrusted.media", 2001))
        install("com.android.systemui", 1001, "0304")
        assertFalse(trust.accepts("com.android.systemui", 1001))
    }
}
