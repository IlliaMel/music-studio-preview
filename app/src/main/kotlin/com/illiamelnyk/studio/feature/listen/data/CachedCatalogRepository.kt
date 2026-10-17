package com.illiamelnyk.studio.feature.listen.data

import android.content.Context
import com.illiamelnyk.studio.core.data.fake.PUBLIC_CATALOG_FIXTURE
import com.illiamelnyk.studio.core.data.local.ApiFailure
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.local.StudioApi
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.feature.listen.domain.CatalogCollection
import com.illiamelnyk.studio.feature.listen.domain.CatalogRepository
import com.illiamelnyk.studio.feature.listen.domain.CatalogSection
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import com.illiamelnyk.studio.feature.listen.domain.CatalogSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class CachedCatalogRepository(
    private val data: DurableStudioRepository,
    private val api: StudioApi,
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CatalogRepository {
    private val cache = CatalogPublicCache(context, api.baseUrl)
    private val mutex = Mutex()
    private val updates = MutableStateFlow<Pair<String, CatalogSnapshot>?>(null)
    private var last: CatalogSnapshot? = null
    private var lastOwner: String? = null

    @Volatile private var validUntil = 0L

    override suspend fun refresh() {
        mutex.withLock { validUntil = 0L }
    }

    override fun observeCollections(): Flow<List<CatalogCollection>> =
        combine(observeSnapshot(), data.observeLiked()) { snapshot, _ ->
            snapshot?.collections.orEmpty().map { collection ->
                collection.copy(
                    tracks = collection.tracks.map { track ->
                        data.catalogTrack(track)
                    },
                )
            }
        }.distinctUntilChanged()

    override fun observeCollection(id: String): Flow<CatalogCollection?> = observeCollections().map { collections ->
        collections.firstOrNull { it.id == id } ?: collections.firstOrNull {
            it.id == when (id) {
                "trending" -> "top-0"
                "new" -> "new-0"
                "vocals" -> "madeonstudio-1"
                "focus" -> "genres-0"
                else -> ""
            }
        }
    }

    override fun observeSection(kind: CatalogSectionKind): Flow<CatalogSection> =
        observeCollections().map { collections ->
            CatalogSection(kind, collections.filter { it.kind == kind })
        }

    private fun observeSnapshot(): Flow<CatalogSnapshot?> = data.session.flatMapLatest { session ->
        flow {
            if (session == null) {
                emit(null)
                return@flow
            }
            val owner = session.user.id.value
            val previous = updates.value?.takeIf { it.first == owner && validUntil != 0L }?.second
            if (previous != null) emit(previous)
            emit(load(session))
            emitAll(updates.filterNotNull().filter { it.first == owner }.map { it.second })
        }.distinctUntilChanged()
    }

    private suspend fun load(session: Session): CatalogSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val owner = session.user.id.value
            val previous = last
            if (previous != null && lastOwner == owner && nowMillis() < validUntil) return@withLock previous
            if (session.dataMode == SessionDataMode.LocalDemo) {
                if (!api.configuration.debug) throw ApiFailure(AppError.UnsupportedMode)
                return@withLock remember(owner, CatalogDocument.parse(JSONObject(PUBLIC_CATALOG_FIXTURE)), 60_000)
            }
            val cached = cache.read()
            val document = when (val response = data.fetchCatalog()) {
                is Outcome.Success -> response.value

                is Outcome.Failure -> {
                    if (response.error !in setOf(AppError.Network, AppError.Timeout)) throw ApiFailure(response.error)
                    if (cached != null && api.configuration.debug) {
                        return@withLock remember(owner, CatalogDocument.parse(cached, api.baseUrl), 1_000)
                    }
                    if (api.configuration.debug) {
                        val fixture = CatalogDocument.parse(JSONObject(PUBLIC_CATALOG_FIXTURE))
                        return@withLock remember(owner, fixture, 1_000)
                    }
                    throw ApiFailure(response.error)
                }
            }
            val snapshot = try {
                CatalogDocument.parse(document, api.baseUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw ApiFailure(AppError.Server)
            }
            cache.write(document)
            remember(owner, snapshot, 60_000)
        }
    }

    private fun remember(owner: String, snapshot: CatalogSnapshot, ttl: Long): CatalogSnapshot {
        data.registerCatalogTracks(snapshot.collections.flatMap { it.tracks }.distinctBy { it.id })
        lastOwner = owner
        last = snapshot
        validUntil = nowMillis() + ttl
        updates.value = owner to snapshot
        return snapshot
    }
}
