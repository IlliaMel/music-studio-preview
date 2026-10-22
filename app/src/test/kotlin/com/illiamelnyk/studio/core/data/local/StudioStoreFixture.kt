package com.illiamelnyk.studio.core.data.local

import androidx.room.withTransaction
import com.illiamelnyk.studio.core.data.local.database.ActiveOwnerEntity
import com.illiamelnyk.studio.core.data.local.database.StudioDatabase
import com.illiamelnyk.studio.core.data.local.database.StudioLegacyImporter
import com.illiamelnyk.studio.core.data.local.database.intent
import com.illiamelnyk.studio.core.data.local.database.snapshot
import java.util.concurrent.Callable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

// Legacy JSON fixture syntax, kept outside production storage and network paths.

/**
 * The last document a test wrote, in a test-only table of the same database: it keeps keys the
 * typed tables do not model (malformed or unknown journals), rolls back with a failed edit and
 * survives reopening the database.
 */
private fun StudioDatabase.fixtureDocument(): JSONObject {
    val db = openHelper.writableDatabase
    db.execSQL("CREATE TABLE IF NOT EXISTS fixture_document (id INTEGER PRIMARY KEY, json TEXT NOT NULL)")
    return db.query("SELECT json FROM fixture_document WHERE id = 0").use { cursor ->
        if (cursor.moveToFirst()) JSONObject(cursor.getString(0)) else JSONObject()
    }
}

private fun StudioDatabase.saveFixtureDocument(document: JSONObject) {
    openHelper.writableDatabase.execSQL(
        "INSERT OR REPLACE INTO fixture_document (id, json) VALUES (0, ?)",
        arrayOf(document.toString()),
    )
}

internal fun StudioStore.read(): JSONObject = runBlocking(Dispatchers.IO) {
    initialize()
    database.withTransaction { fixtureSnapshot() }
}

internal fun StudioStore.change(edit: (JSONObject) -> Unit) = runBlocking(Dispatchers.IO) {
    initialize()
    database.withTransaction {
        val document = fixtureSnapshot()
        edit(document)
        val revision = (database.typedDao().activeAccount()?.active?.revision ?: 0) + 1
        StudioLegacyImporter.clearTyped(database.typedDao())
        database.saveFixtureDocument(document)
        StudioLegacyImporter.import(database, document)
        database.typedDao().putActiveOwner(ActiveOwnerEntity(0, document.stringOrNull("active"), revision))
    }
}

internal fun <T> StudioStore.withDatabase(action: (StudioDatabase) -> T): T = runBlocking(Dispatchers.IO) {
    initialize()
    database.runInTransaction(Callable { action(database) })
}

internal fun <T> StudioStore.withRawDatabase(action: (StudioDatabase) -> T): T =
    runBlocking(Dispatchers.IO) { action(database) }

private suspend fun StudioStore.fixtureSnapshot(): JSONObject {
    val root = database.fixtureDocument()
    val dao = database.typedDao()
    val accounts = dao.accounts().map { it.snapshot() }
    val byOwner = accounts.associateBy { it.session.user.id.value }
    root.keys().asSequence().filter { it.startsWith("account-") }.toList().forEach { key ->
        val owner = key.removePrefix("account-")
        val legacy = AccountJsonCodec.decodeCached(owner, root.optJSONObject(key))
        val current = byOwner[owner]
        if (legacy != null && current == null) root.remove(key)
    }
    accounts.forEach { account ->
        val owner = account.session.user.id.value
        val key = "account-" + owner
        if (AccountJsonCodec.decodeCached(owner, root.optJSONObject(key)) != account) {
            root.put(key, AccountJsonCodec.encode(account))
        }
    }
    dao.activeAccount()?.active?.ownerId?.let { root.put("active", it) } ?: root.remove("active")

    val attempts = dao.allGenerationAttempt().associateBy { it.ownerId }
    root.keys().asSequence().filter { it.startsWith("pending-") }.toList().forEach { key ->
        val value = root.optJSONObject(key)
        if (value?.stringOrNull("signature")?.let { runCatching { JSONObject(it).toRequest() }.isSuccess } == true &&
            key.removePrefix("pending-") !in attempts
        ) {
            root.remove(key)
        }
    }
    attempts.values.filterNot { it.blocked }.forEach { value ->
        val key = "pending-" + value.ownerId
        val existing = root.optJSONObject(key)
        val matchingRequest = existing?.stringOrNull("signature")?.let { source ->
            runCatching {
                val signature = JSONObject(source)
                signature.optInt("variants", 1) == value.variants &&
                    signature.toRequest().copy(referenceAudioUri = null, style = null) ==
                    value.fields.request().copy(referenceAudioUri = null, style = null)
            }.getOrDefault(false)
        } == true
        if (existing?.stringOrNull("key") != value.attemptKey || !matchingRequest) {
            root.put(
                key,
                JSONObject().put("key", value.attemptKey).put(
                    "signature",
                    value.fields.request().toJson().put("variants", value.variants).toString(),
                ),
            )
        }
    }
    val requests = dao.allGenerationRequest().associateBy { it.trackId }
    root.keys().asSequence().filter { it.startsWith("request-") }.toList().forEach { key ->
        if (runCatching { root.getJSONObject(key).toRequest() }.isSuccess &&
            key.removePrefix("request-") !in requests
        ) {
            root.remove(key)
        }
    }
    requests.values.forEach { value ->
        val key = "request-" + value.trackId
        val original = root.optJSONObject(key)
        if (runCatching { original?.toRequest() }.getOrNull() != value.fields.request()) {
            root.put(key, value.fields.request().toJson())
        }
    }
    val retries = dao.allRetryLink().associateBy { it.originalId }
    root.keys().asSequence().filter { it.startsWith("retry-") }.toList().forEach { key ->
        if (key.removePrefix("retry-") !in retries) root.remove(key)
    }
    retries.values.forEach { root.put("retry-" + it.originalId, it.replacementId) }
    val uploads = dao.allReferenceUpload().associateBy { "upload-" + it.ownerId + "-" + it.sourceHash }
    val owners = accounts.map { it.session.user.id.value }
    root.keys().asSequence().filter { it.startsWith("upload-") }.toList().forEach { key ->
        if (root.opt(key) is String && root.getString(key).isNotEmpty() &&
            owners.any { key.startsWith("upload-$it-") } && key !in uploads
        ) {
            root.remove(key)
        }
    }
    uploads.forEach { (key, value) -> root.put(key, value.uploadId) }
    val details = dao.allTrackDetail().associateBy { it.trackId }
    root.keys().asSequence().filter { it.startsWith("detail-") }.toList().forEach { key ->
        if (validDetail(root.optJSONObject(key)) && key.removePrefix("detail-") !in details) root.remove(key)
    }
    details.values.forEach { detail ->
        root.put(
            "detail-" + detail.trackId,
            JSONObject().put("customLyrics", detail.customLyrics).put(
                "words",
                JSONArray(
                    detail.words.map {
                        JSONObject().put("word", it.text).put("startMs", it.start).put("endMs", it.end)
                    },
                ),
            ),
        )
    }
    val deletions = dao.allDeletionIntent().associateBy { it.ownerId }
    root.keys().asSequence().filter { it.startsWith("deletion-") }.toList().forEach { key ->
        if (key.removePrefix("deletion-") !in deletions) root.remove(key)
    }
    deletions.values.forEach { row ->
        val intent = row.intent()
        root.put(
            "deletion-" + intent.ownerId,
            JSONObject().put("ownerId", intent.ownerId).put("token", intent.token)
                .put("remoteConfirmed", intent.remoteConfirmed).put("localPurged", intent.localPurged)
                .put(
                    "remoteCompletionUnverified",
                    intent.remoteCompletionUnverified,
                ).put("requiresReauthentication", intent.requiresReauthentication)
                .put("audio", JSONArray(intent.audio.map { JSONObject().put("id", it.id).put("uri", it.uri) }))
                .put("references", JSONArray(intent.references)),
        )
    }
    val drafts = dao.allRecordingDraft().associateBy { it.key }
    root.keys().asSequence().filter { it.startsWith("recording-pending-") }.toList().forEach { key ->
        if (root.optJSONObject(key)?.has("id") == true && key !in drafts) root.remove(key)
    }
    drafts.values.forEach { row ->
        root.put(
            row.key,
            JSONObject().put("id", row.localId).put("uri", row.uri).put("title", row.title)
                .put("durationMs", row.durationMs).put("confirmed", row.confirmed),
        )
    }
    val receipts = dao.allRecordingReceipt().associateBy {
        it.key.replaceFirst("recording-pending-", "recording-receipt-")
    }
    root.keys().asSequence().filter { it.startsWith("recording-receipt-") }.toList().forEach { key ->
        if (root.opt(key) is String && root.getString(key).isNotEmpty() && key !in receipts) root.remove(key)
    }
    receipts.forEach { (key, value) -> root.put(key, value.trackId) }
    dao.allNotificationOutbox().forEach { outbox ->
        if (outbox.observedIds.isNotEmpty() || root.has("notification-observed-" + outbox.ownerId)) {
            root.put("notification-observed-" + outbox.ownerId, JSONArray(outbox.observedIds))
        }
        if (outbox.dirty) {
            root.put("notification-dirty-" + outbox.ownerId, true)
        } else {
            root.remove("notification-dirty-" + outbox.ownerId)
        }
    }
    return root
}

private fun validDetail(value: JSONObject?): Boolean {
    if (value == null) return false
    if (value.has("customLyrics") && !value.isNull("customLyrics") && value.opt("customLyrics") !is String) return false
    if (!value.has("words")) return true
    val words = value.optJSONArray("words") ?: return false
    return runCatching {
        for (index in 0 until words.length()) {
            val word = words.getJSONObject(index)
            word.getString("word")
            word.getLong("startMs")
            word.getLong("endMs")
        }
    }.isSuccess
}
