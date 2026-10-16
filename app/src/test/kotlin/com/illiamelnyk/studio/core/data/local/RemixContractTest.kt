package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemixContractTest {
    private val ids = listOf("349346a0-577a-4c16-9caa-fbda97aaf16f", "adf92752-96bb-4c1a-91a3-0f16ef14d43d")

    @Test
    fun requestAndTrackRoundTripsKeepEverySourceInOrderAndNetworkUsesOnlyTheArray() {
        val request = GenerationRequest("Blend two original ideas", remixTrackId = ids.first(), remixTrackIds = ids)
        val json = request.toJson()
        assertFalse(json.has("remixTrackId"))
        assertEquals(ids, json.getJSONArray("remixTrackIds").strings())
        assertEquals(ids, JSONObject(json.toString()).toRequest().remixSourceIds)
        val source = FakeCatalog.glassHarbor.copy(remixTrackIds = ids)
        val restored = JSONObject(source.toJson().toString()).toTrack()
        assertEquals(ids, restored.remixTrackIds)
        assertEquals(ids.first(), restored.remixTrackId)
        assertTrue(restored.isRemix)
    }

    @Test
    fun legacySingleSourceCacheMigratesToTheCanonicalArrayWithoutLosingItsIdentity() {
        val legacy = JSONObject().put("prompt", "Original mellow arrangement").put("remixTrackId", ids.first())
        val request = legacy.toRequest()
        assertEquals(listOf(ids.first()), request.remixSourceIds)
        assertEquals(listOf(ids.first()), request.toJson().getJSONArray("remixTrackIds").strings())
        assertFalse(request.toJson().has("remixTrackId"))
    }

    @Test
    fun capabilitiesAllowFourMockSourcesButRejectMultipleLiveSourcesAndDuplicates() {
        val request = GenerationRequest("Blend two original ideas", remixTrackIds = ids)
        val mock = GenerationCapabilities.parse(capabilities("mock", 4))
        assertEquals(null, mock.rejection(request, 1))
        val live = GenerationCapabilities.parse(capabilities("elevenlabs", 1))
        assertEquals(AppError.UnsupportedMode, live.rejection(request, 1))
        assertEquals(null, live.rejection(request.copy(remixTrackIds = ids.take(1)), 1))
        assertEquals(
            AppError.InvalidReference,
            mock.rejection(request.copy(remixTrackIds = listOf(ids.first(), ids.first())), 1),
        )
        assertEquals(
            AppError.InvalidReference,
            mock.rejection(request.copy(referenceAudioUri = "content://reference/audio"), 1),
        )
        val legacy = capabilities("mock", 4).apply { remove("maxRemixSources") }
        assertEquals(AppError.UnsupportedMode, GenerationCapabilities.parse(legacy).rejection(request, 1))
    }

    @Test
    fun canonicalArrayOnlyResponseStillMarksTrackAsARemix() {
        val json = FakeCatalog.glassHarbor.toJson().apply {
            remove("remixTrackId")
            put("remixTrackIds", JSONArray(ids))
        }
        val parsed = json.toTrack()
        assertEquals(ids.first(), parsed.remixTrackId)
        assertEquals(ids, parsed.remixSourceIds)
        assertTrue(parsed.isRemix)
    }

    private fun capabilities(provider: String, maximum: Int): JSONObject = JSONObject()
        .put("provider", provider).put("qualityModes", JSONArray(listOf("auto")))
        .put("customLyrics", true).put("variants", JSONArray(listOf(1, 2)))
        .put("audioReferences", if (provider == "mock") "mock" else "enabled-enterprise")
        .put("liveCatalogRemix", false).put("maxRemixSources", maximum)
}
