package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.data.demo.DemoCatalog
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class StudioStoreFixtureProjectionTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val owner = "fixture-owner"

    @Before fun clean() {
        StudioStore.clearForTests(context)
    }

    @After fun close() {
        StudioStore.closeForTests(context)
    }

    @Test
    fun typedJournalDeletionCannotResurrectThroughASeparateFixtureEdit() = runBlocking {
        val store = StudioStore(context)
        val track = DemoCatalog.tracks.first()
        val receipt = "recording-receipt-" + StudioStore.ownerHash(owner) + "-saved"
        store.change { root ->
            root.put("account-$owner", account())
            root.put("upload-$owner-legacy-hash", "upload-id")
            root.put("detail-" + track.id.value, JSONObject().put("customLyrics", "private").put("words", JSONArray()))
            root.put(receipt, "recording-track")
            root.put("detail-malformed", JSONObject().put("words", "unreadable"))
            root.put("recording-receipt-malformed", JSONObject().put("future", 7))
            root.put("upload-$owner-malformed", JSONObject().put("future", 8))
            root.put("unknown-format", JSONArray().put(42))
        }
        val dao = store.database.typedDao()
        dao.deleteUploads(owner)
        dao.deleteDetail(owner, track.id.value)
        dao.deleteDetail("", "malformed")
        dao.deleteRecordingReceipts(StudioStore.ownerHash(owner))
        val projected = store.read()
        assertFalse(projected.has("upload-$owner-legacy-hash"))
        assertFalse(projected.has("detail-" + track.id.value))
        assertFalse(projected.has(receipt))
        assertEquals("unreadable", projected.getJSONObject("detail-malformed").getString("words"))
        assertEquals(7, projected.getJSONObject("recording-receipt-malformed").getInt("future"))
        assertEquals(8, projected.getJSONObject("upload-$owner-malformed").getInt("future"))
        store.change { it.put("unrelated-edit", true) }
        assertTrue(dao.allReferenceUpload().none { it.ownerId == owner })
        assertEquals(null, store.detailFor(owner, track.id.value))
        assertEquals(null, store.recordingReceipt(receipt.replaceFirst("recording-receipt-", "recording-pending-")))
        assertEquals(42, store.read().getJSONArray("unknown-format").getInt(0))
    }

    @Test
    fun unchangedRequestsKeepUnknownFieldsButSameKeyChangedAttemptsReflectTypedTruth() = runBlocking {
        val store = StudioStore(context)
        val id = DemoCatalog.tracks.first().id.value
        val request = GenerationRequest("Original request prompt")
        val rawRequest = request.toJson().put("future-request-field", JSONObject().put("version", 99))
        val signature = request.toJson().put("variants", 1).put("future-signature-field", "keep")
        store.change {
            it.put("account-$owner", account()).put("request-$id", rawRequest)
                .put("pending-$owner", JSONObject().put("key", "same-paid-key").put("signature", signature.toString()))
        }
        assertEquals(
            99,
            store.read().getJSONObject("request-$id").getJSONObject("future-request-field").getInt("version"),
        )
        assertEquals(signature.toString(), store.read().getJSONObject("pending-$owner").getString("signature"))
        val changed = GenerationRequest("Different canonical request")
        store.putGenerationAttempt(owner, PendingGeneration("same-paid-key", changed, 2))
        val actual = JSONObject(store.read().getJSONObject("pending-$owner").getString("signature"))
        assertEquals(changed, actual.toRequest())
        assertEquals(2, actual.getInt("variants"))
        assertFalse(actual.has("future-signature-field"))
        store.saveAccount(checkNotNull(store.readAccount(owner)), generationRequests = mapOf(id to changed))
        assertEquals(changed, store.read().getJSONObject("request-$id").toRequest())
    }

    @Test
    fun malformedPaidAttemptRemainsBlockedAndItsOriginalDocumentStaysUntouched() = runBlocking {
        val store = StudioStore(context)
        val original = JSONObject().put("key", "must-not-spend-again").put("future", JSONArray().put(17))
        store.change { it.put("account-$owner", account()).put("pending-$owner", original) }
        assertTrue(checkNotNull(store.generationAttempt(owner)).blocked)
        assertEquals(original.toString(), store.read().getJSONObject("pending-$owner").toString())
        store.change { it.put("unrelated", true) }
        assertTrue(checkNotNull(store.generationAttempt(owner)).blocked)
        assertEquals(original.toString(), store.read().getJSONObject("pending-$owner").toString())
    }

    private fun account(): JSONObject = JSONObject().put("profile", JSONObject().put("id", owner))
        .put("tracks", JSONArray().put(DemoCatalog.tracks.first().toJson()))
}
