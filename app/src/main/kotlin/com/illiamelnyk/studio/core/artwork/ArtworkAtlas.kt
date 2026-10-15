package com.illiamelnyk.studio.core.artwork

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ArtworkAtlasTile(
    val id: String,
    val image: Bitmap,
    val left: Int,
    val top: Int,
    val side: Int,
    val palette: List<Int>,
)

internal class ArtworkAtlas(
    val version: String,
    val tiles: List<ArtworkAtlasTile>,
    private val aliases: Map<String, ArtworkAtlasTile>,
    private val fallback: List<ArtworkAtlasTile>,
) {
    fun resolve(seed: String): ArtworkAtlasTile =
        aliases[normalize(seed)] ?: fallback[Math.floorMod(seed.hashCode(), fallback.size)]

    companion object {
        fun read(assets: AssetManager): ArtworkAtlas {
            val manifest = assets.open("artwork/manifest-v1.json").bufferedReader().use { JSONObject(it.readText()) }
            require(manifest.getInt("schemaVersion") == 1)
            val atlases = manifest.getJSONArray("atlases")
            val images = (0 until atlases.length()).associate { index ->
                val atlas = atlases.getJSONObject(index)
                val bitmap = assets.open("artwork/" + atlas.getString("file")).use {
                    requireNotNull(BitmapFactory.decodeStream(it))
                }
                require(bitmap.width == atlas.getInt("width") && bitmap.height == atlas.getInt("height"))
                atlas.getString("id") to (atlas to bitmap)
            }
            val aliases = mutableMapOf<String, ArtworkAtlasTile>()
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
                val tile = ArtworkAtlasTile(
                    entry.getString("id"),
                    image,
                    left + (right - left - side) / 2,
                    top + (bottom - top - side) / 2,
                    side,
                    (0 until palette.length()).map { android.graphics.Color.parseColor(palette.getString(it)) },
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
            return ArtworkAtlas(manifest.getString("fixtureVersion"), tiles, aliases, fallback)
        }

        private fun normalize(seed: String): String = seed.lowercase().filter(Char::isLetterOrDigit)
    }
}

internal object ArtworkAtlasCache {
    private val mutex = Mutex()

    @Volatile private var atlas: ArtworkAtlas? = null
    val current: ArtworkAtlas? get() = atlas
    private var retryAfter = 0L

    suspend fun load(assets: AssetManager, dispatcher: CoroutineDispatcher = Dispatchers.IO): ArtworkAtlas? =
        mutex.withLock {
            atlas?.let { return@withLock it }
            if (System.currentTimeMillis() < retryAfter) return@withLock null
            try {
                withContext(dispatcher) { ArtworkAtlas.read(assets) }.also { atlas = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("StudioArtwork", "Artwork unavailable", error)
                retryAfter = System.currentTimeMillis() + RETRY_MS
                null
            }
        }

    const val RETRY_MS = 5_000L
}
