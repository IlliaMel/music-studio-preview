package com.illiamelnyk.studio.core.ui.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ArtworkLibraryTest {
    private val assets get() = RuntimeEnvironment.getApplication().assets

    @Test
    fun preparedPreviewsAndPlayerDetailShareExactlyTheSameSourceAndCrop() = runTest {
        val library = requireNotNull(ArtworkLibraryLoader().load(assets))
        library.tiles.filter { it.detail != null }.forEach { tile ->
            val source = requireNotNull(tile.detail)
            assertEquals(source.side / 2, tile.size.width)
            assertEquals(source.left / 2, tile.offset.x)
            assertEquals(source.top / 2, tile.offset.y)
            assertEquals(source.width / 2, tile.image.width)
            assertEquals(source.height / 2, tile.image.height)
        }
    }

    @Test
    fun bundledTilesStayInsideTheirAtlasAndHeroCoversHavePlayerResolution() {
        val library = ArtworkLibrary.read(assets)
        assertEquals("studio-original-artwork-v1", library.version)
        assertEquals(20, library.tiles.size)
        library.tiles.forEach {
            assertTrue(it.offset.x >= 0 && it.offset.y >= 0)
            assertTrue(it.offset.x + it.size.width <= it.image.width)
            assertTrue(it.offset.y + it.size.height <= it.image.height)
            assertEquals(it.size.width, it.size.height)
        }
        assertTrue(library.resolve("Glass Harbor").size.width >= 608)
        assertTrue(library.resolve("Night Drive Through Kyiv").size.width >= 608)
    }

    @Test
    fun catalogAndToolAliasesSelectTheirOwnArtwork() {
        val library = ArtworkLibrary.read(assets)
        val aliases = mapOf(
            "Glass Harbor" to "glass-harbor", "Night Drive Through Kyiv" to "night-drive",
            "Paper Moons" to "paper-moons", "Cold Brew Mornings" to "cold-brew",
            "Signal Fire" to "signal-fire", "Copper Sky" to "copper-sky",
            "Lanterns on the Vistula" to "lanterns", "Static Bloom" to "static-bloom",
            "Afterglow Avenue" to "afterglow", "Slow Tide" to "slow-tide",
            "Kinetic" to "kinetic", "Low Orbit" to "low-orbit",
            "VoiceToSong" to "voice-to-song", "Genreshift" to "genreshift",
            "SampleToSong" to "sample-to-song", "Unplugged" to "unplugged",
            "VoiceMemoToSong" to "voice-memo", "HumToSong" to "hum-to-song",
        )
        aliases.forEach { (seed, id) -> assertEquals(seed, id, library.resolve(seed).id) }
        assertSame(library.resolve("VoiceToSong"), library.resolve("Voice to Song"))
        assertSame(library.resolve("Glass Harbor"), library.resolve("glass-harbor"))
        val restored = ArtworkLibrary.read(assets)
        assertEquals(library.resolve("new-generation-id").id, restored.resolve("new-generation-id").id)
    }

    @Test
    fun sharedLoadReusesDecodedBitmaps() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = assets
        val firstLoad = async { ArtworkLibraryCache.load(manager, dispatcher) }
        val secondLoad = async { ArtworkLibraryCache.load(manager, dispatcher) }
        val first = requireNotNull(firstLoad.await())
        val second = requireNotNull(secondLoad.await())
        assertSame(first, second)
        assertSame(first.resolve("Cold Brew Mornings").image, second.resolve("Signal Fire").image)
    }

    @Test
    fun bundledAtlasBytesMatchPublishedManifestHashes() {
        val manifest = assets.open("artwork/manifest-v1.json").bufferedReader().use { JSONObject(it.readText()) }
        for (section in listOf("atlases", "detailAtlases")) {
            val atlases = manifest.getJSONArray(section)
            for (index in 0 until atlases.length()) {
                val atlas = atlases.getJSONObject(index)
                val hash = assets.open("artwork/" + atlas.getString("file")).use {
                    MessageDigest.getInstance("SHA-256").digest(it.readBytes())
                }.joinToString("") { "%02x".format(it.toInt() and 255) }
                assertEquals(atlas.getString("file"), atlas.getString("sha256"), hash)
            }
        }
    }

    @Test
    fun allMusicAndFallbackIdentitiesHaveLargeDetailSourcesWithoutEagerDecode() {
        val library = ArtworkLibrary.read(assets)
        val detailed = library.tiles.filter { it.detail != null }
        assertEquals(15, detailed.size)
        detailed.forEach { tile ->
            val source = requireNotNull(tile.detail)
            assertTrue(source.side >= 885)
            assertTrue(source.side > tile.size.width)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open("artwork/${source.file}").use { BitmapFactory.decodeStream(it, null, options) }
            assertEquals(source.width, options.outWidth)
            assertEquals(source.height, options.outHeight)
            assertTrue(source.left >= 0 && source.top >= 0)
            assertTrue(source.left + source.side <= source.width)
            assertTrue(source.top + source.side <= source.height)
        }
        assertNull(library.resolve("VoiceToSong").detail)
    }

    @Test
    fun detailedCoversReusePairDecodeAndEvictLeastRecentlyUsedPairWithinBudget() = runTest {
        val library = ArtworkLibrary.read(assets)
        val manager = assets
        var decodes = 0
        val pairBytes = 1774 * 887 * 4
        val loader = ArtworkDetailLoader(maxBytes = pairBytes * 2) { _, file ->
            decodes++
            val source = requireNotNull(library.tiles.first { it.detail?.file == file }.detail)
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        suspend fun load(seed: String) = requireNotNull(loader.load(manager, library.resolve(seed), dispatcher))
        val coffee = load("Cold Brew Mornings")
        val fire = load("Signal Fire")
        assertEquals(1, decodes)
        assertEquals("cold-brew", coffee.id)
        assertEquals("signal-fire", fire.id)
        assertEquals(885, coffee.size.width)
        assertTrue(coffee.offset.x < fire.offset.x)
        load("Copper Sky")
        load("Static Bloom")
        assertEquals(3, decodes)
        assertTrue(loader.cachedBytes <= pairBytes * 2)
        load("Cold Brew Mornings")
        assertEquals(4, decodes)
        assertNull(loader.load(manager, library.resolve("VoiceToSong"), dispatcher))
        assertEquals(4, decodes)
    }

    @Test
    fun defaultDetailBudgetKeepsTheLiveSessionsAndThePlayerCoverDecoded() = runTest {
        val library = ArtworkLibrary.read(assets)
        val manager = assets
        var decodes = 0
        val loader = ArtworkDetailLoader { _, file ->
            decodes++
            val source = requireNotNull(library.tiles.first { it.detail?.file == file }.detail)
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        suspend fun load(seed: String) = requireNotNull(loader.load(manager, library.resolve(seed), dispatcher))
        val stations = listOf("Late Night Lo-fi", "Ambient Spaces", "Piano Room")
        assertEquals(3, stations.map { library.resolve(it).detail?.file }.distinct().size)
        stations.forEach { load(it) }
        load("Cold Brew Mornings")
        assertEquals(4, decodes)
        stations.forEach { load(it) }
        assertEquals(4, decodes)
    }

    @Test
    fun failedDetailDecodeKeepsThumbnailAvailableAndCanRetry() = runTest {
        val thumbnail = ArtworkLibrary.read(assets).resolve("Signal Fire")
        val manager = assets
        var attempts = 0
        val loader = ArtworkDetailLoader { _, _ ->
            attempts++
            if (attempts == 1) error("Temporary decode failure")
            val source = requireNotNull(thumbnail.detail)
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        assertNull(loader.load(manager, thumbnail, dispatcher))
        assertEquals(278, thumbnail.size.width)
        assertEquals(thumbnail.id, requireNotNull(loader.load(manager, thumbnail, dispatcher)).id)
    }
}
