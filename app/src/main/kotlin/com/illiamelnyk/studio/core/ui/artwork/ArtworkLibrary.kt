package com.illiamelnyk.studio.core.ui.artwork

import android.content.res.AssetManager
import android.graphics.BitmapFactory
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
import com.illiamelnyk.studio.core.artwork.ArtworkAtlas
import com.illiamelnyk.studio.core.artwork.ArtworkAtlasCache
import com.illiamelnyk.studio.core.artwork.ArtworkDetailSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class ArtworkTile(
    val id: String,
    val image: ImageBitmap,
    val offset: IntOffset,
    val size: IntSize,
    val palette: List<Color>,
    val detail: ArtworkDetailSource? = null,
)

internal class ArtworkLibrary private constructor(private val atlas: ArtworkAtlas, assets: AssetManager? = null) {
    val version = atlas.version
    private val images = atlas.tiles.map { it.image }.distinct().associateWith { it.asImageBitmap() }

    // List previews and player details use the same artwork, so upgrading resolution cannot
    // change the composition or crop. Half-size shared pairs keep the startup memory bounded.
    private val previews = if (assets == null) {
        emptyMap()
    } else {
        atlas.tiles.mapNotNull { it.detail }
            .distinctBy { it.file }.associate { source ->
                val bitmap = assets.open("artwork/${source.file}").use {
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    requireNotNull(BitmapFactory.decodeStream(it, null, options))
                }
                source.file to bitmap.asImageBitmap()
            }
    }
    val tiles = atlas.tiles.map {
        ArtworkTile(
            it.id,
            it.detail?.let { source -> previews[source.file] } ?: images.getValue(it.image),
            it.detail?.takeIf { source -> source.file in previews }?.let { source ->
                IntOffset(source.left / 2, source.top / 2)
            } ?: IntOffset(it.left, it.top),
            it.detail?.takeIf { source -> source.file in previews }?.let { source ->
                IntSize(source.side / 2, source.side / 2)
            } ?: IntSize(it.side, it.side),
            it.palette.map { color -> Color(color) },
            it.detail,
        )
    }
    private val byId = tiles.associateBy { it.id }
    fun resolve(seed: String): ArtworkTile = byId.getValue(atlas.resolve(seed).id)

    companion object {
        fun read(assets: AssetManager): ArtworkLibrary = ArtworkLibrary(ArtworkAtlas.read(assets))
        fun from(atlas: ArtworkAtlas): ArtworkLibrary = ArtworkLibrary(atlas)
        fun prepared(atlas: ArtworkAtlas, assets: AssetManager): ArtworkLibrary = ArtworkLibrary(atlas, assets)
    }
}

internal class ArtworkLibraryLoader(
    private val loadAtlas: suspend (AssetManager, CoroutineDispatcher) -> ArtworkAtlas? = { assets, dispatcher ->
        ArtworkAtlasCache.load(assets, dispatcher)
    },
) {
    private val mutex = Mutex()

    @Volatile private var library: ArtworkLibrary? = null
    val current: ArtworkLibrary? get() = library

    suspend fun load(assets: AssetManager, dispatcher: CoroutineDispatcher = Dispatchers.IO): ArtworkLibrary? =
        withContext(dispatcher) {
            mutex.withLock {
                library
                    ?: loadAtlas(assets, dispatcher)?.let { ArtworkLibrary.prepared(it, assets) }?.also { library = it }
            }
        }
}

internal object ArtworkLibraryCache {
    private val loader = ArtworkLibraryLoader()
    val current: ArtworkLibrary? get() = loader.current

    suspend fun load(assets: AssetManager, dispatcher: CoroutineDispatcher = Dispatchers.IO): ArtworkLibrary? =
        loader.load(assets, dispatcher)

    const val RETRY_MS = ArtworkAtlasCache.RETRY_MS
}

@Composable
internal fun rememberArtwork(seed: String, requestedSidePx: Int = 0): ArtworkTile? {
    val assets = LocalContext.current.applicationContext.assets
    val library by produceState<ArtworkLibrary?>(ArtworkLibraryCache.current, assets) {
        while (value == null) {
            value = ArtworkLibraryCache.load(assets)
            if (value == null) delay(ArtworkLibraryCache.RETRY_MS)
        }
    }
    val thumbnail = remember(library, seed) { library?.resolve(seed) }
    val needsDetail = thumbnail != null && requestedSidePx > thumbnail.size.width && thumbnail.detail != null
    // Read in composition: a decoded cover draws in the same frame its size becomes known, instead of
    // a frame of placeholder while the load below restarts with the new keys.
    val cached = remember(thumbnail, needsDetail) {
        thumbnail?.takeIf { needsDetail }?.let(ArtworkDetailCache.loader::peek)
    }
    val detailed by produceState(cached, assets, thumbnail, needsDetail) {
        value = if (needsDetail) ArtworkDetailCache.loader.load(assets, requireNotNull(thumbnail)) else null
    }
    // Until the sharp cover is decoded, its list preview stands in: the same crop at half the
    // resolution. A gradient in the wait read as a colored flash on phones, where the first visit
    // to Listen decodes three large covers.
    return if (needsDetail) detailed?.takeIf { it.id == thumbnail.id } ?: cached ?: thumbnail else thumbnail
}
