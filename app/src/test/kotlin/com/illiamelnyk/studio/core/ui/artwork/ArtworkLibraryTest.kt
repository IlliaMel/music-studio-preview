package com.illiamelnyk.studio.core.ui.artwork

import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertSame(first.resolve("Glass Harbor").image, second.resolve("Paper Moons").image)
    }

    @Test
    fun bundledAtlasBytesMatchPublishedManifestHashes() {
        val manifest = assets.open("artwork/manifest-v1.json").bufferedReader().use { JSONObject(it.readText()) }
        val atlases = manifest.getJSONArray("atlases")
        for (index in 0 until atlases.length()) {
            val atlas = atlases.getJSONObject(index)
            val hash = assets.open("artwork/" + atlas.getString("file")).use {
                MessageDigest.getInstance("SHA-256").digest(it.readBytes())
            }.joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(atlas.getString("file"), atlas.getString("sha256"), hash)
        }
    }
}
