package com.illiamelnyk.studio.core.data.local.database

import com.illiamelnyk.studio.core.data.local.AccountJsonCodec
import com.illiamelnyk.studio.core.data.local.DeletionAudio
import com.illiamelnyk.studio.core.data.local.StudioStore
import com.illiamelnyk.studio.core.data.local.array
import com.illiamelnyk.studio.core.data.local.objects
import com.illiamelnyk.studio.core.data.local.stringOrNull
import com.illiamelnyk.studio.core.data.local.strings
import com.illiamelnyk.studio.core.data.local.toRequest
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import org.json.JSONObject

/**
 * Runs once, in the transaction that sets the import marker. A malformed generation attempt is
 * blocked rather than replayed; other malformed journal entries are skipped.
 */
internal object StudioLegacyImporter {
    suspend fun import(database: StudioDatabase, root: JSONObject) {
        val dao = database.typedDao()
        val accounts = root.keys().asSequence().filter { it.startsWith("account-") }.mapNotNull { key ->
            AccountJsonCodec.decodeCached(key.removePrefix("account-"), root.optJSONObject(key))
        }.toList()
        accounts.forEach { StudioStore.persistAccount(dao, it) }
        val owners = accounts.map { it.session.user.id.value }
        val trackOwners = accounts.flatMap { account ->
            account.tracks.map { it.id.value to account.session.user.id.value }
        }.groupBy({ it.first }, { it.second })
        fun ownerOf(id: String): String? = trackOwners[id]?.distinct()?.singleOrNull()
        for (key in root.keys().asSequence().toList()) {
            // A malformed individual journal is retained losslessly, never interpreted as a new attempt.
            try {
                when {
                    key.startsWith("pending-") -> {
                        val owner = key.removePrefix("pending-")
                        val value = root.getJSONObject(key)
                        val signature = JSONObject(value.requiredString("signature"))
                        val request = signature.toRequest()
                        val verifiedSource = root.keys().asSequence().filter { it.startsWith("request-") }
                            .filter { ownerOf(it.removePrefix("request-")) == owner }
                            .mapNotNull { root.optJSONObject(it) }.firstOrNull {
                                request.referenceUploadId != null &&
                                    it.stringOrNull("referenceUploadId") == request.referenceUploadId &&
                                    it.stringOrNull("referenceAudioUri") != null
                            }?.stringOrNull("referenceAudioUri")
                        dao.putGenerationAttempt(
                            GenerationAttemptEntity(
                                owner,
                                value.requiredString("key"),
                                signature.optInt("variants", 1),
                                request.copy(
                                    referenceAudioUri = request.referenceAudioUri ?: verifiedSource,
                                ).storedFields(),
                            ),
                        )
                    }

                    key.startsWith("request-") -> {
                        val id = key.removePrefix("request-")
                        val request = root.getJSONObject(key).toRequest().storedFields()
                        dao.putGenerationRequest(GenerationRequestEntity(id, ownerOf(id).orEmpty(), request))
                    }

                    key.startsWith("retry-") -> {
                        val id = key.removePrefix("retry-")
                        dao.putRetryLink(RetryLinkEntity(id, ownerOf(id).orEmpty(), root.requiredString(key)))
                    }

                    key.startsWith("upload-") -> {
                        val owner = owners.sortedByDescending(String::length)
                            .firstOrNull { key.startsWith("upload-$it-") }
                        if (owner != null) {
                            dao.putReferenceUpload(
                                ReferenceUploadEntity(
                                    owner,
                                    key.removePrefix("upload-$owner-"),
                                    root.requiredString(key),
                                ),
                            )
                        }
                    }

                    key.startsWith("detail-") -> {
                        val id = key.removePrefix("detail-")
                        val value = root.getJSONObject(key)
                        val words = value.array("words").objects().map {
                            StoredWord(it.getString("word"), it.getLong("startMs"), it.getLong("endMs"))
                        }
                        dao.putTrackDetail(
                            TrackDetailEntity(id, ownerOf(id).orEmpty(), value.stringOrNull("customLyrics"), words),
                        )
                    }

                    key.startsWith("deletion-") -> {
                        val value = root.getJSONObject(key)
                        dao.putDeletionIntent(
                            DeletionIntentEntity(
                                ownerId = value.getString("ownerId"),
                                token = value.stringOrNull("token"),
                                audio = value.array("audio").objects().map {
                                    DeletionAudio(it.getString("id"), it.stringOrNull("uri"))
                                },
                                references = value.array("references").strings(),
                                remoteConfirmed = value.optBoolean("remoteConfirmed"),
                                localPurged = value.optBoolean("localPurged"),
                                remoteCompletionUnverified = value.optBoolean("remoteCompletionUnverified"),
                                requiresReauthentication = value.optBoolean("requiresReauthentication"),
                            ),
                        )
                    }

                    key.startsWith("recording-pending-") -> {
                        val value = root.getJSONObject(key)
                        val hash = key.removePrefix("recording-pending-").take(64)
                        val owner = owners.singleOrNull { StudioStore.ownerHash(it) == hash }
                        dao.putRecordingDraft(
                            RecordingDraftEntity(
                                key,
                                owner,
                                hash,
                                value.getString("id"),
                                value.getString("uri"),
                                value.getString("title"),
                                value.getLong("durationMs"),
                                value.optBoolean("confirmed"),
                            ),
                        )
                    }

                    key.startsWith("recording-receipt-") -> {
                        val hash = key.removePrefix("recording-receipt-").take(64)
                        val owner = owners.singleOrNull { StudioStore.ownerHash(it) == hash }
                        val draftKey = key.replaceFirst("recording-receipt-", "recording-pending-")
                        dao.putRecordingReceipt(RecordingReceiptEntity(draftKey, owner, hash, root.requiredString(key)))
                    }
                }
            } catch (_: org.json.JSONException) {
                blockMalformedAttempt(dao, key)
            } catch (_: IllegalArgumentException) {
                blockMalformedAttempt(dao, key)
            }
        }
        val notificationOwners = owners + root.keys().asSequence().filter { it.startsWith("notification-observed-") }
            .map { it.removePrefix("notification-observed-") }.toList()
        notificationOwners.distinct().forEach { owner ->
            val observed = runCatching { root.array("notification-observed-$owner").strings() }
                .getOrDefault(emptyList())
            dao.putNotificationOutbox(
                NotificationOutboxEntity(owner, observed, root.optBoolean("notification-dirty-$owner")),
            )
        }
        dao.putActiveOwner(ActiveOwnerEntity(0, root.stringOrNull("active"), 0))
    }

    /** Android getString coerces objects/numbers; malformed journal values must remain quarantined. */
    private fun JSONObject.requiredString(key: String): String = (opt(key) as? String)?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Invalid legacy journal string")

    private suspend fun blockMalformedAttempt(dao: StudioTypedDao, key: String) {
        if (key.startsWith("pending-")) {
            dao.putGenerationAttempt(
                GenerationAttemptEntity(
                    key.removePrefix("pending-"),
                    "unreadable-legacy",
                    1,
                    GenerationRequest("").storedFields(),
                    blocked = true,
                ),
            )
        }
    }

    suspend fun clearTyped(dao: StudioTypedDao) {
        dao.clearStoredAccount()
        dao.clearActiveOwner()
        dao.clearGenerationAttempt()
        dao.clearGenerationRequest()
        dao.clearRetryLink()
        dao.clearReferenceUpload()
        dao.clearTrackDetail()
        dao.clearDeletionIntent()
        dao.clearRecordingDraft()
        dao.clearRecordingReceipt()
        dao.clearNotificationOutbox()
    }
}
