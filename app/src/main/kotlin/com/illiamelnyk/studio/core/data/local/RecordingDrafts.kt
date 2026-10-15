package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import org.json.JSONObject

internal class RecordingDrafts(
    private val store: StudioStore,
    private val files: RecordingFiles,
    private val api: StudioApi,
) {
    data class Saved(val key: String, val track: Track)

    suspend fun save(account: AccountSnapshot, uri: String, title: String, duration: Duration): Saved {
        if (duration.inWholeMilliseconds !in 1..600_000) throw ApiFailure(AppError.InvalidReference)
        val normalized = title.trim().take(80).ifBlank { "Recording" }
        val owner = account.session.user.id.value
        val signature = digest(uri + "\u0000" + normalized + "\u0000" + duration.inWholeMilliseconds)
        val key = prefix(owner) + signature
        val receipt = store.read().stringOrNull(receiptKey(key))
        if (receipt != null) {
            val confirmed = account.tracks.find { it.id.value == receipt }
                ?: throw ApiFailure(AppError.InvalidReference)
            return Saved(key, confirmed)
        }
        val pending = store.read().optJSONObject(key) ?: run {
            val id = UUID.randomUUID().toString()
            val copied = files.copyToStaging(uri, "recording-" + id, owner)
            val value = JSONObject().put("id", id).put("uri", copied)
                .put("title", normalized).put("durationMs", duration.inWholeMilliseconds)
            try {
                store.change { it.put(key, value) }
            } catch (error: Exception) {
                files.delete(TrackId("recording-" + id), copied, owner)
                throw error
            }
            value
        }
        return put(account, key, pending)
    }

    suspend fun recover(account: AccountSnapshot): List<Saved> {
        val owner = account.session.user.id.value
        account.tracks.filter { it.id.value.startsWith("recording-") && it.audioUri?.startsWith("file:") == true }
            .forEach { track ->
                val id = track.id.value.removePrefix("recording-")
                UUID.fromString(id)
                val key = prefix(owner) + "legacy-" + id
                if (!store.read().has(key) && !store.read().has(receiptKey(key))) {
                    val value = JSONObject().put("id", id).put("uri", track.audioUri)
                        .put("title", track.title).put("durationMs", track.duration.inWholeMilliseconds)
                    store.change { it.put(key, value) }
                }
            }
        try {
            files.deleteStagedExcept(owner, pending(owner).map { (_, value) -> value.getString("uri") })
        } catch (_: java.io.IOException) {
            android.util.Log.w("StudioData", "Cannot reclaim recording staging")
        }
        return pending(owner).mapNotNull { (key, value) ->
            try {
                if (value.optBoolean("confirmed")) {
                    cleanup(key, value)
                    return@mapNotNull null
                }
                put(account, key, value)
            } catch (failure: ApiFailure) {
                if (failure.error == AppError.Unauthorized) throw failure
                if (failure.serverCode == "RECORDING_DELETED") {
                    try {
                        discard(owner, TrackId("recording-" + value.getString("id")))
                    } catch (_: java.io.IOException) {
                        Unit
                    }
                }
                null
            } catch (_: java.io.IOException) {
                null
            }
        }
    }

    fun acknowledge(saved: List<Saved>) {
        saved.forEach { value ->
            val pending = store.read().optJSONObject(value.key) ?: return@forEach
            confirm(value.key, pending, value.track.id)
            cleanup(value.key, pending)
        }
    }

    fun discard(owner: String, id: TrackId) {
        pending(owner).filter { (_, value) -> "recording-" + value.getString("id") == id.value }
            .forEach { (key, value) ->
                confirm(key, value, id)
                cleanup(key, value)
            }
    }

    private fun confirm(key: String, pending: JSONObject, id: TrackId) {
        store.change { root ->
            root.put(receiptKey(key), id.value)
            root.put(key, JSONObject(pending.toString()).put("confirmed", true))
        }
    }

    private fun cleanup(key: String, pending: JSONObject) {
        files.deletePrivateCopy(pending.getString("uri"))
        store.change { it.remove(key) }
    }

    fun pendingAudio(owner: String): List<DeletionAudio> = pending(owner).map { (_, value) ->
        DeletionAudio("recording-" + value.getString("id"), value.getString("uri"))
    }

    fun purgeJournal(root: JSONObject, owner: String) {
        root.keys().asSequence().filter {
            it.startsWith(prefix(owner)) || it.startsWith(receiptKey(prefix(owner)))
        }.toList().forEach(root::remove)
    }

    fun track(json: JSONObject, account: AccountSnapshot): Track {
        val id = UUID.fromString(json.getString("id")).toString()
        val audio = URI(json.getString("audioUrl"))
        val origin = URI(api.baseUrl)
        if (audio.scheme != origin.scheme || audio.host != origin.host || audio.port != origin.port ||
            audio.rawUserInfo != null || audio.rawQuery != null || audio.rawFragment != null ||
            audio.rawPath != "/v1/recordings/" + id + "/audio"
        ) {
            throw ApiFailure(AppError.Server)
        }
        val duration = json.getLong("durationMs")
        val title = json.getString("title").trim()
        if (duration !in 1..600_000 || title.length !in 1..80) throw ApiFailure(AppError.Server)
        val trackId = "recording-" + id
        return Track(
            id = TrackId(trackId), title = title, artist = account.session.user.displayName.orEmpty(),
            prompt = "", style = null, duration = duration.milliseconds, model = MusicModel.V2_5,
            instrumental = false, coverSeed = CoverSeed(trackId), status = TrackStatus.Ready,
            createdAt = Instant.parse(json.getString("createdAt")),
            isLiked = trackId in account.likes, audioUri = audio.toString(),
            audioHeaders = account.token?.let(api::audioHeaders).orEmpty(),
        )
    }

    private suspend fun put(account: AccountSnapshot, key: String, pending: JSONObject): Saved {
        val token = account.token ?: throw ApiFailure(AppError.Unauthorized)
        val response = api.putRecording(
            pending.getString("id"),
            pending.getString("uri"),
            pending.getString("title"),
            pending.getLong("durationMs"),
            token,
        )
        if (response.getString("id") != pending.getString("id")) throw ApiFailure(AppError.Server)
        return Saved(key, track(response, account))
    }

    private fun pending(owner: String): List<Pair<String, JSONObject>> = store.read().let { root ->
        root.keys().asSequence().filter { it.startsWith(prefix(owner)) }
            .map { it to root.getJSONObject(it) }.toList()
    }

    private fun receiptKey(key: String) = key.replaceFirst("recording-pending-", "recording-receipt-")
    private fun prefix(owner: String) = "recording-pending-" + digest(owner) + "-"
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
