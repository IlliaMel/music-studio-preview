package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.local.database.RecordingDraftEntity
import com.illiamelnyk.studio.core.data.network.RecordingDto
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class RecordingDrafts(
    private val store: StudioStore,
    private val files: RecordingFiles,
    private val api: StudioApi,
    private val persistDraft: suspend (RecordingDraftEntity) -> Unit = store::saveRecordingDraft,
) {
    data class Saved(val key: String, val track: Track)

    suspend fun save(account: AccountSnapshot, uri: String, title: String, duration: Duration): Saved {
        if (duration.inWholeMilliseconds !in 1..600_000) throw ApiFailure(AppError.InvalidReference)
        val normalized = title.trim().take(80).ifBlank { "Recording" }
        val owner = account.session.user.id.value
        val key = prefix(owner) + digest(uri + "\u0000" + normalized + "\u0000" + duration.inWholeMilliseconds)
        val receipt = store.recordingReceipt(key)
        if (receipt != null) {
            val track = account.tracks.find { it.id.value == receipt } ?: throw ApiFailure(AppError.InvalidReference)
            return Saved(key, track)
        }
        val pending = store.recordingDraft(key) ?: run {
            val id = UUID.randomUUID().toString()
            val copied = files.copyToStaging(uri, "recording-" + id, owner)
            val value =
                RecordingDraftEntity(
                    key,
                    owner,
                    digest(owner),
                    id,
                    copied,
                    normalized,
                    duration.inWholeMilliseconds,
                    false,
                )
            try {
                persistDraft(value)
            } catch (error: Exception) {
                // Cancellation can arrive after SQLite committed. Never destroy a handed-off file.
                // A failed readback is ambiguous, so preserve the copy for durable recovery.
                val definitelyUnreferenced = withContext(NonCancellable) {
                    runCatching { store.recordingDraft(key)?.uri != copied }.getOrDefault(false)
                }
                if (definitelyUnreferenced) files.delete(TrackId("recording-" + id), copied, owner)
                throw error
            }
            value
        }
        return put(account, pending)
    }

    suspend fun recover(account: AccountSnapshot): List<Saved> {
        val owner = account.session.user.id.value
        account.tracks.filter {
            it.id.value.startsWith("recording-") && it.audioUri?.startsWith("file:") == true
        }.forEach { track ->
            val id = track.id.value.removePrefix("recording-")
            UUID.fromString(id)
            val key = prefix(owner) + "legacy-" + id
            if (store.recordingDraft(key) == null && store.recordingReceipt(key) == null) {
                store.saveRecordingDraft(
                    RecordingDraftEntity(
                        key,
                        owner,
                        digest(owner),
                        id,
                        requireNotNull(track.audioUri),
                        track.title,
                        track.duration.inWholeMilliseconds,
                        false,
                    ),
                )
            }
        }
        try {
            files.deleteStagedExcept(owner, store.recordingDrafts(owner).map { it.uri })
        } catch (
            _: java.io.IOException,
        ) {
            android.util.Log.w("StudioData", "Cannot reclaim recording staging")
        }
        return store.recordingDrafts(owner).mapNotNull { value ->
            try {
                if (value.confirmed) {
                    cleanup(value)
                    return@mapNotNull null
                }
                put(account, value)
            } catch (failure: ApiFailure) {
                if (failure.error == AppError.Unauthorized) throw failure
                if (failure.serverCode == "RECORDING_DELETED") {
                    try {
                        discard(owner, TrackId("recording-" + value.localId))
                    } catch (
                        _: java.io.IOException,
                    ) {
                        Unit
                    }
                }
                null
            } catch (_: java.io.IOException) {
                null
            }
        }
    }

    suspend fun acknowledge(saved: List<Saved>) {
        saved.forEach { value ->
            val pending = store.recordingDraft(value.key) ?: return@forEach
            store.confirmRecording(pending, value.track.id.value)
            cleanup(pending)
        }
    }

    suspend fun discard(owner: String, id: TrackId) {
        store.recordingDrafts(owner).filter { "recording-" + it.localId == id.value }.forEach { pending ->
            store.confirmRecording(pending, id.value)
            cleanup(pending)
        }
    }

    private suspend fun cleanup(pending: RecordingDraftEntity) {
        files.deletePrivateCopy(pending.uri)
        store.deleteRecordingDraft(pending.key)
    }

    suspend fun pendingAudio(owner: String): List<DeletionAudio> = store.recordingDrafts(owner).map {
        DeletionAudio("recording-" + it.localId, it.uri)
    }

    fun track(dto: RecordingDto, account: AccountSnapshot): Track {
        val id = UUID.fromString(dto.id).toString()
        val audio = URI(dto.audioUrl)
        val expected = URI(api.trackAudioUri(TrackId("recording-" + id)))
        if (audio != expected) throw ApiFailure(AppError.Server)
        val title = dto.title.trim()
        if (dto.durationMs !in 1..600_000 || title.length !in 1..80) throw ApiFailure(AppError.Server)
        val trackId = "recording-" + id
        return Track(
            id = TrackId(trackId), title = title, artist = account.session.user.displayName.orEmpty(),
            prompt = "", style = null, duration = dto.durationMs.milliseconds, model = MusicModel.V2_5,
            instrumental = false, coverSeed = CoverSeed(trackId), status = TrackStatus.Ready,
            createdAt = Instant.parse(dto.createdAt), isLiked = trackId in account.likes, audioUri = audio.toString(),
        )
    }

    private suspend fun put(account: AccountSnapshot, pending: RecordingDraftEntity): Saved {
        val token = account.token ?: throw ApiFailure(AppError.Unauthorized)
        val response = api.putRecording(pending.localId, pending.uri, pending.title, pending.durationMs, token)
        if (response.id != pending.localId) throw ApiFailure(AppError.Server)
        return Saved(pending.key, track(response, account))
    }

    private fun prefix(owner: String) = "recording-pending-" + digest(owner) + "-"
    private fun digest(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {
            "%02x".format(it)
        }
}
