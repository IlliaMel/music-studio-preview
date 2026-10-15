package com.illiamelnyk.studio.core.data.playback

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.scale
import com.illiamelnyk.studio.core.artwork.ArtworkAtlasCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class AndroidMediaArtwork(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(seed: String): Bitmap? = withContext(dispatcher) {
        val atlas = ArtworkAtlasCache.load(context.assets, dispatcher) ?: return@withContext null
        val tile = atlas.resolve(seed)
        mutex.withLock {
            cache.get(tile.id) ?: run {
                val cropped = Bitmap.createBitmap(tile.image, tile.left, tile.top, tile.side, tile.side)
                val bitmap = if (tile.side > MAX_SIDE) {
                    cropped.scale(MAX_SIDE, MAX_SIDE, true)
                } else {
                    cropped
                }
                if (cropped !== bitmap) cropped.recycle()
                cache.put(tile.id, bitmap)
                bitmap
            }
        }
    }

    companion object {
        private const val MAX_SIDE = 320
        private val mutex = Mutex()
        private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    }
}
