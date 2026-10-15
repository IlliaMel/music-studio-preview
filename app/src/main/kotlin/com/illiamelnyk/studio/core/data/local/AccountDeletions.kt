package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.TrackId
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

internal data class DeletionAudio(val id: String, val uri: String?)

internal data class AccountDeletionIntent(
    val ownerId: String,
    val token: String?,
    val audio: List<DeletionAudio>,
    val references: List<String>,
    val remoteConfirmed: Boolean,
    val localPurged: Boolean = false,
)

internal data class AccountDeletionResult(val ownerId: String, val remoteError: AppError?)

internal class AccountDeletions(
    private val store: StudioStore,
    private val api: StudioApi,
    private val recordings: RecordingFiles,
) {
    fun pending(): List<AccountDeletionIntent> = store.read().let { root ->
        root.keys().asSequence().filter { it.startsWith(PREFIX) }
            .mapNotNull { root.optJSONObject(it)?.decodeDeletion() }.toList()
    }

    fun begin(account: AccountSnapshot): AccountDeletionIntent {
        pending().firstOrNull { it.ownerId == account.session.user.id.value }?.let { return it }
        val snapshot = store.read()
        val intent = AccountDeletionIntent(
            ownerId = account.session.user.id.value,
            token = account.token,
            audio = account.tracks.map { DeletionAudio(it.id.value, it.audioUri) },
            references = account.tracks.mapNotNull {
                snapshot.optJSONObject("request-" + it.id.value)?.stringOrNull("referenceAudioUri")
            },
            remoteConfirmed = account.token == null,
        )
        persist(intent)
        return intent
    }

    suspend fun execute(original: AccountDeletionIntent): AccountDeletionResult {
        var intent = original
        var remoteError: AppError? = null
        if (!intent.remoteConfirmed) {
            val token = intent.token
            if (token == null) {
                remoteError = AppError.Unauthorized
            } else {
                try {
                    try {
                        val profile = api.request("GET", "/v1/me", token)
                        if (profile.getString("id") != intent.ownerId) throw ApiFailure(AppError.Unauthorized)
                    } catch (failure: ApiFailure) {
                        if (failure.serverCode != "ACCOUNT_DELETING") throw failure
                    }
                    api.request("DELETE", "/v1/me", token)
                    intent = intent.copy(remoteConfirmed = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ApiFailure) {
                    remoteError = failure.error
                } catch (_: Exception) {
                    remoteError = AppError.Unknown
                }
                if (intent.remoteConfirmed) persist(intent)
            }
        }
        if (!intent.localPurged) {
            intent.audio.forEach { recordings.delete(TrackId(it.id), it.uri) }
            recordings.deleteReferences(intent.ownerId, intent.references)
            recordings.deletePlaybackState(intent.ownerId)
            store.change { root ->
                root.remove("account-" + intent.ownerId)
                root.remove("pending-" + intent.ownerId)
                if (root.stringOrNull("active") == intent.ownerId) root.remove("active")
                root.keys().asSequence().filter { it.startsWith("upload-" + intent.ownerId + "-") }
                    .toList().forEach(root::remove)
                intent.audio.forEach { audio ->
                    listOf("request-", "rename-", "retry-", "detail-").forEach { root.remove(it + audio.id) }
                }
                if (intent.remoteConfirmed) {
                    root.remove(PREFIX + intent.ownerId)
                } else {
                    val pending = intent.copy(audio = emptyList(), references = emptyList(), localPurged = true)
                    root.put(PREFIX + intent.ownerId, pending.encodeDeletion())
                }
            }
        } else if (intent.remoteConfirmed) {
            store.change { it.remove(PREFIX + intent.ownerId) }
        }
        return AccountDeletionResult(intent.ownerId, remoteError)
    }

    private fun persist(intent: AccountDeletionIntent) {
        store.change { it.put(PREFIX + intent.ownerId, intent.encodeDeletion()) }
    }

    private companion object {
        const val PREFIX = "deletion-"
    }
}

private fun AccountDeletionIntent.encodeDeletion(): JSONObject = JSONObject().apply {
    put("ownerId", ownerId)
    put("token", token)
    put("remoteConfirmed", remoteConfirmed)
    put("localPurged", localPurged)
    put("audio", JSONArray(audio.map { JSONObject().put("id", it.id).put("uri", it.uri) }))
    put("references", JSONArray(references))
}

private fun JSONObject.decodeDeletion(): AccountDeletionIntent = AccountDeletionIntent(
    ownerId = getString("ownerId"),
    token = stringOrNull("token"),
    remoteConfirmed = optBoolean("remoteConfirmed"),
    localPurged = optBoolean("localPurged"),
    audio = array("audio").objects().map { DeletionAudio(it.getString("id"), it.stringOrNull("uri")) },
    references = array("references").strings(),
)
