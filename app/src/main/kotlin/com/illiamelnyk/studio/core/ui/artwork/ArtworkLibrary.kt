package com.illiamelnyk.studio.core.ui.artwork

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ArtworkTile(
    val id: String,
    val image: ImageBitmap,
    val offset: IntOffset,
    val size: IntSize,
    val palette: List<Color>,
)

internal class ArtworkLibrary(
    val version: String,
    val tiles: List<ArtworkTile>,
    private val aliases: Map<String, ArtworkTile>,
    private val fallback: List<ArtworkTile>,
) {
    fun resolve(seed: String): ArtworkTile =
        aliases[normalize(seed)] ?: fallback[Math.floorMod(seed.hashCode(), fallback.size)]

    companion object {
        fun read(assets: AssetManager): ArtworkLibrary {
            val manifest = assets.open("artwork/manifest-v1.json").bufferedReader().use { JSONObject(it.readText()) }
            require(manifest.getInt("schemaVersion") == 1)
            val atlases = manifest.getJSONArray("atlases")
            val images = (0 until atlases.length()).associate { index ->
                val atlas = atlases.getJSONObject(index)
                val bitmap = assets.open("artwork/" + atlas.getString("file")).use {
                    requireNotNull(BitmapFactory.decodeStream(it))
                }.asImageBitmap()
                require(bitmap.width == atlas.getInt("width") && bitmap.height == atlas.getInt("height"))
                atlas.getString("id") to (atlas to bitmap)
            }
            val aliases = mutableMapOf<String, ArtworkTile>()
            val entries = manifest.getJSONArray("tiles")
            val tiles = (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                val (atlas, image) = images.getValue(entry.getString("atlas"))
                val columns = atlas.getInt("columns")
                val rows = atlas.getInt("rows")
                val cell = entry.getInt("index")
                require(columns > 0 && rows > 0 && cell in 0 until columns * rows)
                val inset = atlas.getInt("insetPx")
                val left = cell % columns * image.width / columns + inset
                val top = cell / columns * image.height / rows + inset
                val right = (cell % columns + 1) * image.width / columns - inset
                val bottom = (cell / columns + 1) * image.height / rows - inset
                val side = minOf(right - left, bottom - top)
                require(side > 0)
                val palette = entry.getJSONArray("palette")
                val tile = ArtworkTile(
                    entry.getString("id"),
                    image,
                    IntOffset(left + (right - left - side) / 2, top + (bottom - top - side) / 2),
                    IntSize(side, side),
                    (0 until palette.length()).map { Color(android.graphics.Color.parseColor(palette.getString(it))) },
                )
                require(tile.palette.size == 3)
                val names = entry.getJSONArray("aliases")
                for (name in 0 until names.length()) aliases[normalize(names.getString(name))] = tile
                tile
            }
            val fallbackIds = manifest.getJSONArray("fallbackTiles")
            val fallback = (0 until fallbackIds.length()).map { index ->
                tiles.first { it.id == fallbackIds.getString(index) }
            }
            require(fallback.isNotEmpty())
            return ArtworkLibrary(manifest.getString("fixtureVersion"), tiles, aliases, fallback)
        }

        private fun normalize(seed: String): String = seed.lowercase().filter(Char::isLetterOrDigit)
    }
}

internal object ArtworkLibraryCache {
    private val mutex = Mutex()

    @Volatile private var library: ArtworkLibrary? = null
    val current: ArtworkLibrary? get() = library
    private var retryAfter = 0L

    suspend fun load(assets: AssetManager, dispatcher: CoroutineDispatcher = Dispatchers.IO): ArtworkLibrary? =
        mutex.withLock {
            library?.let { return@withLock it }
            if (System.currentTimeMillis() < retryAfter) return@withLock null
            try {
                withContext(dispatcher) { ArtworkLibrary.read(assets) }.also { library = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("StudioArtwork", "Artwork unavailable; retaining generated fallback", error)
                retryAfter = System.currentTimeMillis() + RETRY_MS
                null
            }
        }

    const val RETRY_MS = 5_000L
}

@Composable
internal fun rememberArtwork(seed: String): ArtworkTile? {
    val assets = LocalContext.current.applicationContext.assets
    val library by produceState<ArtworkLibrary?>(ArtworkLibraryCache.current, assets) {
        while (value == null) {
            value = ArtworkLibraryCache.load(assets)
            if (value == null) delay(ArtworkLibraryCache.RETRY_MS)
        }
    }
    return remember(library, seed) { library?.resolve(seed) }
}
