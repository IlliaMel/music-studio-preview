package com.illiamelnyk.studio.feature.listen.data

import android.content.Context
import app.cash.turbine.test
import com.illiamelnyk.studio.core.data.fake.PUBLIC_CATALOG_FIXTURE
import com.illiamelnyk.studio.core.data.local.ApiAuthMode
import com.illiamelnyk.studio.core.data.local.ApiConfiguration
import com.illiamelnyk.studio.core.data.local.ApiFailure
import com.illiamelnyk.studio.core.data.local.DurableStudioRepository
import com.illiamelnyk.studio.core.data.local.StudioApi
import com.illiamelnyk.studio.core.data.local.StudioStore
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.feature.listen.domain.CatalogSectionKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CatalogRepositoryTest {
    private lateinit var context: Context

    @Before
    fun clean() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.listFiles()?.filter {
            it.name.startsWith("studio-state-v1.json") || it.name.startsWith("public-catalog-v1-")
        }?.forEach { it.delete() }
    }

    @Test
    fun packagedFixtureMatchesCanonicalFallbackAndHasTwentyDistinctRankedSongs() {
        val asset = context.assets.open("catalog/catalog-v1.json").bufferedReader().use { it.readText() }
        assertEquals(asset.trim(), PUBLIC_CATALOG_FIXTURE.trim())
        val snapshot = CatalogDocument.parse(JSONObject(asset))
        val chart = snapshot.collection("top-0")!!
        assertEquals(20, chart.tracks.size)
        assertEquals(20, chart.tracks.map { it.id }.toSet().size)
        val trend = snapshot.trends.first { it.id == chart.trendSnapshotId }
        assertEquals(chart.tracks.map { it.id.value }, trend.entries.map { it.trackId })
        assertEquals((1..20).toList(), trend.entries.map { it.rank })
        assertEquals("fixture", trend.provider)
        assertTrue(snapshot.fictional)
        assertTrue(snapshot.notice.contains("not measured popularity"))
    }

    @Test
    fun serverTitleAndChartOrderReplaceFixtureAndConcurrentSectionsShareOneFetch() = runTest {
        val api = MemoryCatalogApi(context)
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInServer()
        val catalog = CachedCatalogRepository(data, api, context)
        CatalogSectionKind.entries.map { kind -> async { catalog.observeSection(kind).first() } }.awaitAll()
        assertEquals(1, api.catalogReads)
        val initial = catalog.observeCollection("top-0").first()!!
        val reversed = initial.tracks.map { it.id.value }.reversed()
        val chartJson = api.document.getJSONArray("collections").objects().first { it.getString("id") == "top-0" }
        chartJson.put("trackIds", JSONArray(reversed))
        api.document.getJSONArray("trendSnapshots").getJSONObject(0).put(
            "entries",
            JSONArray(
                reversed.mapIndexed { index, id ->
                    JSONObject().put("trackId", id).put("rank", index + 1).put("playCount", 20_000 - index * 731)
                },
            ),
        )
        api.document.put("catalogVersion", "server-fixture-update")
        api.document.getJSONArray("tracks").getJSONObject(0).put("title", "Server selected title")
        catalog.refresh()
        val updated = catalog.observeCollection("top-0").first()!!
        assertEquals(reversed, updated.tracks.map { it.id.value })
        val all = catalog.observeCollections().first().flatMap { it.tracks }
        assertEquals("Server selected title", all.first { it.id.value == "glass-harbor" }.title)
        assertNotEquals(initial.tracks.map { it.id }, updated.tracks.map { it.id })
        assertEquals(2, api.catalogReads)
    }

    @Test
    fun publicCacheSurvivesRestartAndKeepsPrivateLikesAndBearerOut() = runTest {
        val api = MemoryCatalogApi(context)
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInServer()
        val catalog = CachedCatalogRepository(data, api, context)
        val glass = catalog.observeCollections().first().flatMap { it.tracks }.first { it.id.value == "glass-harbor" }
        assertTrue(data.setLiked(glass.id, true) is Outcome.Success)
        assertTrue(data.observeLiked().first().single().isLiked)
        api.failure = AppError.Network
        val restarted = CachedCatalogRepository(data, api, context)
        val cached = restarted.observeCollections().first()
        assertTrue(cached.flatMap { it.tracks }.filter { it.id == glass.id }.all { it.isLiked })
        val cacheText = context.filesDir.listFiles()!!.first { it.name.startsWith("public-catalog-v1-") }.readText()
        assertFalse(cacheText.contains("Authorization"))
        assertFalse(cacheText.contains("dev:illia"))
        assertFalse(cacheText.contains("isLiked"))
        data.signOut()
        data.signInDemo()
        assertTrue(data.observeLiked().first().isEmpty())
    }

    @Test
    fun serverMetadataUpdatesPrivateLikedLookupWithoutPersonalizingPublicCache() = runTest {
        val api = MemoryCatalogApi(context)
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInServer()
        val catalog = CachedCatalogRepository(data, api, context)
        val glass = catalog.observeCollections().first().flatMap { it.tracks }.first { it.id.value == "glass-harbor" }
        data.setLiked(glass.id, true)
        api.document.getJSONArray("tracks").getJSONObject(0).put("title", "Updated public name")
        catalog.refresh()
        catalog.observeCollections().first()
        assertEquals("Updated public name", data.observeLiked().first().single().title)
        assertTrue(data.observeById(glass.id).first()!!.isLiked)
        assertTrue(catalog.observeCollections().first().flatMap { it.tracks }.first { it.id == glass.id }.isLiked)
    }

    @Test
    fun duplicateUnknownOutOfOrderAndForeignAudioReferencesAreRejected() {
        val corruptions: List<(JSONObject) -> Unit> = listOf(
            { it.getJSONArray("tracks").put(it.getJSONArray("tracks").getJSONObject(0)) },
            { it.getJSONArray("collections").getJSONObject(0).getJSONArray("trackIds").put("unknown-id") },
            {
                it.getJSONArray("trendSnapshots").getJSONObject(0).getJSONArray("entries")
                    .getJSONObject(0).put("rank", 9)
            },
            { it.getJSONArray("tracks").getJSONObject(0).put("audioUrl", "https://foreign.test/private") },
            { it.getJSONArray("tracks").getJSONObject(0).put("isLiked", true) },
            { it.put("schemaVersion", 2) },
        )
        corruptions.forEach { corrupt ->
            val document = serverDocument()
            corrupt(document)
            assertTrue(runCatching { CatalogDocument.parse(document, ORIGIN) }.isFailure)
        }
    }

    @Test
    fun cacheIsScopedToApiOriginAndMalformedRefreshCannotReplaceGoodMetadata() {
        val cache = CatalogPublicCache(context, ORIGIN)
        cache.write(serverDocument())
        assertEquals("2026-10-16.fixture.1", cache.read()!!.getString("catalogVersion"))
        assertEquals(null, CatalogPublicCache(context, "https://different.test").read())
        val malformed = serverDocument().put("catalogVersion", "bad").put("likes", JSONArray(listOf("private")))
        assertTrue(runCatching { cache.write(malformed) }.isFailure)
        assertEquals("2026-10-16.fixture.1", cache.read()!!.getString("catalogVersion"))
    }

    @Test
    fun releaseDoesNotTurnNetworkFailureIntoBundledOfflineProduct() = runTest {
        val api = MemoryCatalogApi(context, ApiConfiguration(ORIGIN, ApiAuthMode.Firebase, false))
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInFirebase("firebase-user")
        api.failure = AppError.Network
        val catalog = CachedCatalogRepository(data, api, context)
        val error = try {
            catalog.observeCollections().first()
            null
        } catch (failure: ApiFailure) {
            failure.error
        }
        assertEquals(AppError.Network, error)
    }

    @Test
    fun releaseAlsoFailsClosedWhenOnlyAnOlderPublicCacheIsAvailable() = runTest {
        CatalogPublicCache(context, ORIGIN).write(serverDocument())
        val api = MemoryCatalogApi(context, ApiConfiguration(ORIGIN, ApiAuthMode.Firebase, false))
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInFirebase("firebase-user")
        api.failure = AppError.Network
        val catalog = CachedCatalogRepository(data, api, context)
        val error = try {
            catalog.observeCollections().first()
            null
        } catch (failure: ApiFailure) {
            failure.error
        }
        assertEquals(AppError.Network, error)
    }

    @Test
    fun activeCollectorsReactToLikesAndGlobalRefreshWithoutRefetchingPerLike() = runTest {
        val api = MemoryCatalogApi(context)
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInServer()
        val catalog = CachedCatalogRepository(data, api, context)
        catalog.observeCollection("featured-0").test {
            val before = awaitItem()!!
            val track = before.tracks.first()
            val reads = api.catalogReads
            data.setLiked(track.id, true)
            assertTrue(awaitItem()!!.tracks.first().isLiked)
            assertEquals(reads, api.catalogReads)
            val changed = api.document.getJSONArray("tracks").objects().first { it.getString("id") == track.id.value }
            changed.put("title", "Live public metadata update")
            catalog.refresh()
            catalog.observeSection(CatalogSectionKind.Top).first()
            assertEquals("Live public metadata update", awaitItem()!!.tracks.first().title)
            assertEquals(reads + 1, api.catalogReads)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ownerSwitchCannotCarryPriorOwnerLikesIntoCurrentCatalogResolution() = runTest {
        val api = MemoryCatalogApi(context)
        val data = DurableStudioRepository(context, StudioStore(context), api, backgroundScope)
        data.signInServer()
        val catalog = CachedCatalogRepository(data, api, context)
        val track = catalog.observeCollection("top-0").first()!!.tracks.first()
        data.setLiked(track.id, true)
        val priorOwnerTrack = data.observeLiked().first().single()
        assertTrue(priorOwnerTrack.isLiked)
        data.signOut()
        assertFalse(data.catalogTrack(priorOwnerTrack).isLiked)
        data.signInDemo()
        assertFalse(data.catalogTrack(priorOwnerTrack).isLiked)
        assertTrue(catalog.observeCollections().first().flatMap { it.tracks }.none { it.isLiked })
    }

    private class MemoryCatalogApi(
        context: Context,
        config: ApiConfiguration = ApiConfiguration(ORIGIN, ApiAuthMode.Development, true),
    ) : StudioApi(context, config) {
        var document = serverDocument()
        var failure: AppError? = null
        var catalogReads = 0
        private val likes = mutableSetOf<String>()
        override suspend fun request(
            method: String,
            path: String,
            token: String,
            body: JSONObject?,
            key: String?,
        ): JSONObject {
            if (path == "/v1/catalog") {
                catalogReads++
                failure?.let { throw ApiFailure(it) }
                return JSONObject(document.toString())
            }
            if (path == "/v1/me") return JSONObject().put("id", "server-owner").put("displayName", "Server user")
            if (path == "/v1/me/quota") return JSONObject().put("limit", 25).put("remaining", 25)
            if (path == "/v1/likes") return JSONObject().put("trackIds", JSONArray(likes.toList()))
            if (path.startsWith("/v1/likes/")) {
                if (method == "PUT") likes.add(path.substringAfterLast('/'))
                return JSONObject()
            }
            if (path.startsWith("/v1/tracks?")) return JSONObject().put("tracks", JSONArray())
            if (path == "/v1/playlists") return JSONObject().put("playlists", JSONArray())
            throw ApiFailure(AppError.Server)
        }
    }

    companion object {
        private const val ORIGIN = "https://api.studio.test"
        private fun serverDocument(): JSONObject = JSONObject(PUBLIC_CATALOG_FIXTURE).also { doc ->
            doc.getJSONArray("tracks").objects().forEach {
                it.put("audioUrl", ORIGIN + "/v1/catalog/tracks/" + it.getString("id") + "/audio")
            }
        }
        private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
    }
}
