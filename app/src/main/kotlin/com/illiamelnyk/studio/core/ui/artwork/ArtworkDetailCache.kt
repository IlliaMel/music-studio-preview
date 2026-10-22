package com.illiamelnyk.studio.core.ui.artwork

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Large covers only. Evicted images are not recycled: a visible Compose node can still own one.
 *
 * A pair decodes to about 6.3 MB and the budget keeps five: Live Sessions alone draw from three pairs,
 * and with room for two every visit to Listen decoded one of them again behind a placeholder.
 */
internal class ArtworkDetailLoader(
    maxBytes: Int = 32 * 1024 * 1024,
    private val decode: (AssetManager, String) -> Bitmap = { assets, file ->
        assets.open("artwork/$file").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    },
) {
    private val mutex = Mutex()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    internal val cachedBytes: Int get() = cache.size()

    fun peek(thumbnail: ArtworkTile): ArtworkTile? {
        val source = thumbnail.detail ?: return null
        val bitmap = cache.get(source.file) ?: return null
        return thumbnail.copy(
            image = bitmap.asImageBitmap(),
            offset = IntOffset(source.left, source.top),
            size = IntSize(source.side, source.side),
        )
    }

    suspend fun load(
        assets: AssetManager,
        thumbnail: ArtworkTile,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): ArtworkTile? = withContext(dispatcher) {
        val source = thumbnail.detail ?: return@withContext null
        mutex.withLock {
            try {
                val bitmap = cache.get(source.file) ?: decode(assets, source.file).also {
                    require(it.width == source.width && it.height == source.height)
                    cache.put(source.file, it)
                }
                thumbnail.copy(
                    image = bitmap.asImageBitmap(),
                    offset = IntOffset(source.left, source.top),
                    size = IntSize(source.side, source.side),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("StudioArtwork", "Detailed artwork unavailable", error)
                null
            }
        }
    }
}

internal object ArtworkDetailCache {
    val loader = ArtworkDetailLoader()
}
