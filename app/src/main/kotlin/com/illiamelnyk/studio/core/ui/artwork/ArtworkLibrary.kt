package com.illiamelnyk.studio.core.ui.artwork

import android.content.res.AssetManager
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
)

internal class ArtworkLibrary private constructor(private val atlas: ArtworkAtlas) {
    val version = atlas.version
    private val images = atlas.tiles.map { it.image }.distinct().associateWith { it.asImageBitmap() }
    val tiles = atlas.tiles.map {
        ArtworkTile(
            it.id,
            images.getValue(it.image),
            IntOffset(it.left, it.top),
            IntSize(it.side, it.side),
            it.palette.map { color -> Color(color) },
        )
    }
    private val byId = tiles.associateBy { it.id }
    fun resolve(seed: String): ArtworkTile = byId.getValue(atlas.resolve(seed).id)

    companion object {
        fun read(assets: AssetManager): ArtworkLibrary = ArtworkLibrary(ArtworkAtlas.read(assets))
        fun from(atlas: ArtworkAtlas): ArtworkLibrary = ArtworkLibrary(atlas)
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
                library ?: loadAtlas(assets, dispatcher)?.let(ArtworkLibrary::from)?.also { library = it }
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
