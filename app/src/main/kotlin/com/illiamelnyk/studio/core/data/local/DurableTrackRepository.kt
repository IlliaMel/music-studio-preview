package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.network.toCreateTrackDto
import com.illiamelnyk.studio.core.data.network.toTrack
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.RecordingReference
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Account-scoped TrackRepository implementation. All mutations share the owner transaction boundary. */
internal class DurableTrackRepository(private val runtime: StudioAccountRuntime) : TrackRepository {
    override fun observeMine(): Flow<List<Track>> = runtime.state.map { it?.tracks.orEmpty() }.distinctUntilChanged()

    // A local account has no server to wait for; a signed-in one waits for its first refresh.
    override fun observeLibrarySynced(): Flow<Boolean> = combine(runtime.state, runtime.sync.refreshedOwners) {
            account,
            refreshed,
        ->
        account?.token == null || account.session.user.id.value in refreshed
    }.distinctUntilChanged()

    override fun observeLiked(): Flow<List<Track>> = combine(runtime.state, runtime.publicCatalogTracks) {
            account,
            catalog,
        ->
        account?.tracks.orEmpty().filter {
            it.isLiked
        } + catalog.filter {
            it.id.value in account?.likes.orEmpty()
        }.map {
            runtime.catalogTrack(it).copy(isLiked = true)
        }
    }.distinctUntilChanged()
    override fun observeById(id: TrackId): Flow<Track?> =
        combine(runtime.state, runtime.publicCatalogTracks) { account, catalog ->
            account?.tracks?.find {
                it.id == id
            } ?: catalog.find {
                it.id == id
            }?.let {
                runtime.catalogTrack(it).copy(isLiked = it.id.value in account?.likes.orEmpty())
            }
        }.distinctUntilChanged()
    override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> =
        runtime.operation { createLocked(request, variants) }

    private suspend fun createLocked(request: GenerationRequest, variants: Int, retryOf: String? = null): List<Track> {
        if (variants !in 1..2 ||
            request.prompt.trim().length !in 10..2000 ||
            (request.customLyrics?.length ?: 0) > 10000 ||
            (request.instrumental && request.customLyrics != null)
        ) {
            throw ApiFailure(AppError.InvalidPrompt(null))
        }
        val account = runtime.requireAccount()
        if (!account.session.user.canGenerate) throw ApiFailure(AppError.GenerationNotAllowed)
        val remixSources = request.remixTrackIds
        if (remixSources.size > 4 || remixSources.distinct().size != remixSources.size ||
            (remixSources.isNotEmpty() && (request.referenceAudioUri != null || request.referenceUploadId != null))
        ) {
            throw ApiFailure(AppError.InvalidReference)
        }
        remixSources.forEach { sourceId ->
            val source = account.tracks.find { it.id.value == sourceId }
                ?: runtime.publicCatalogTracks.value.find { it.id.value == sourceId }
                ?: throw ApiFailure(AppError.InvalidReference)
            if (!source.canRemixOriginal) {
                throw ApiFailure(AppError.InvalidReference)
            }
        }

        val batch = if (account.token != null) {
            runtime.api.preflightGeneration(account.token, request, variants)
            val owner = account.session.user.id.value
            val pending = runtime.store.generationAttempt(owner)
            if (pending?.blocked == true) throw ApiFailure(AppError.ProviderOutcomeUnknown)
            val comparable = request.copy(
                referenceUploadId = pending?.request?.referenceUploadId,
                referenceAudioUri = null,
                style = null,
            )
            val samePendingFields = pending != null && pending.variants == variants &&
                pending.request.copy(referenceAudioUri = null, style = null) == comparable
            val sameSource = pending?.request?.referenceAudioUri == request.referenceAudioUri
            if (samePendingFields && request.referenceAudioUri != null &&
                pending.request.referenceUploadId != null && pending.request.referenceAudioUri == null
            ) {
                // Old hash-only upload journals cannot establish source identity. Never spend a new key.
                throw ApiFailure(AppError.ProviderOutcomeUnknown)
            }
            var uploaded = request.referenceUploadId
            if (uploaded == null && request.referenceAudioUri != null) {
                uploaded = pending?.takeIf { samePendingFields && sameSource }?.request?.referenceUploadId
                    ?: runtime.store.uploadFor(owner, request.referenceAudioUri)
                    ?: runtime.api.upload(request.referenceAudioUri, account.token).also {
                        runtime.store.saveUpload(owner, request.referenceAudioUri, it)
                    }
            }
            var storedRequest = request.copy(referenceUploadId = uploaded, style = null)
            val key = pending?.takeIf { it.request == storedRequest && it.variants == variants }?.key
                ?: UUID.randomUUID().toString()
            val reusedAmbiguousAttempt = pending?.key == key
            runtime.store.putGenerationAttempt(owner, PendingGeneration(key, storedRequest, variants))
            try {
                val created = try {
                    runtime.api.createTracks(account.token, storedRequest.toCreateTrackDto(variants, uploaded), key)
                } catch (failure: ApiFailure) {
                    val source = request.referenceAudioUri
                    if (failure.serverCode != "UPLOAD_NOT_FOUND" || source == null) throw failure
                    // The server checks this key before upload expiry. Keep the same key when
                    // refreshing a rejected reference so a concurrent acceptance cannot spend twice.
                    uploaded = runtime.api.upload(source, account.token)
                    runtime.store.saveUpload(owner, source, uploaded)
                    storedRequest = request.copy(referenceUploadId = uploaded, style = null)
                    runtime.store.putGenerationAttempt(owner, PendingGeneration(key, storedRequest, variants))
                    runtime.api.createTracks(account.token, storedRequest.toCreateTrackDto(variants, uploaded), key)
                }
                created.map { it.toTrack(account.session.user.displayName.orEmpty()) }
            } catch (failure: ApiFailure) {
                if (failure.error == AppError.GenerationNotAllowed || failure.error == AppError.ServiceDailyLimit) {
                    // A policy gate can run before server idempotency lookup. It proves this send
                    // was refused, but cannot disprove acceptance of an earlier lost response.
                    withContext(NonCancellable) {
                        if (!reusedAmbiguousAttempt) {
                            runCatching { runtime.store.clearGenerationAttempt(owner, key) }.exceptionOrNull()?.let {
                                if (it !== failure) failure.addSuppressed(it)
                            }
                        }
                        if (failure.error == AppError.GenerationNotAllowed) {
                            runCatching {
                                val current = runtime.requireAccount()
                                runtime.save(
                                    current.copy(
                                        session = current.session.copy(
                                            user = current.session.user.copy(canGenerate = false),
                                        ),
                                    ),
                                )
                            }.exceptionOrNull()?.let {
                                if (it !== failure) failure.addSuppressed(it)
                            }
                        }
                    }
                }
                throw failure
            }
        } else {
            val reset = runtime.resetCredits(account)
            if (reset.credits.remaining == 0) throw ApiFailure(AppError.QuotaExceeded)

            val group = UUID.randomUUID().toString()
            List(variants) { index ->
                Track(
                    TrackId("local-" + group + "-" + index),
                    request.prompt.trim().take(40),
                    account.session.user.displayName.orEmpty(),
                    request.prompt, request.style, request.duration ?: 2.minutes, request.model, request.instrumental,
                    CoverSeed(group + index),
                    TrackStatus.Queued,
                    runtime.clock.now(),
                    false,
                    customLyrics = request.customLyrics,
                    generationId = group, variantIndex = index, variants = variants,
                    quality = request.quality,
                    remixTrackIds = remixSources,
                    referenceUploadId = request.referenceUploadId,
                )
            }
        }
        val current = runtime.requireAccount()
        val updated = if (account.token == null) {
            runtime.resetCredits(current).let {
                it.copy(credits = it.credits.copy(remaining = it.credits.remaining - 1))
            }
        } else {
            // An accepted authenticated request also confirms refreshed credentials are usable.
            current.copy(requiresAuthentication = false)
        }
        val batchIds = batch.map { it.id }.toSet()
        runtime.save(
            updated.copy(tracks = batch + current.tracks.filterNot { it.id in batchIds }),
            generationRequests = batch.associate { it.id.value to request },
            completeAttempt = account.token != null,
            retryLinks = if (retryOf != null) mapOf(retryOf to batch.single().id.value) else emptyMap(),
        )
        if (account.token != null) {
            runtime.guarded { runtime.updateQuota() }
        }
        return batch
    }

    override suspend fun retry(id: TrackId): Outcome<Track> = runtime.operation {
        val account = runtime.requireAccount()
        val track = account.tracks.find { it.id == id } ?: throw ApiFailure(AppError.Unknown)
        val failure = track.status as? TrackStatus.Failed ?: return@operation track
        if (failure.error in setOf(
                AppError.ProviderOutcomeUnknown,
                AppError.UnsupportedMode,
                AppError.InvalidReference,
            )
        ) {
            throw ApiFailure(failure.error)
        }
        val replacementId = runtime.store.retryFor(account.session.user.id.value, id.value)
        val replacement = account.tracks.find { it.id.value == replacementId }
        if (replacement != null && replacement.status !is TrackStatus.Failed) return@operation replacement
        val original = runtime.store.requestFor(account.session.user.id.value, id.value)
            ?: GenerationRequest(
                track.prompt,
                track.duration,
                track.model,
                quality = track.quality,
                instrumental = track.instrumental,
                customLyrics = track.customLyrics,
                remixTrackIds = track.remixTrackIds,
                referenceUploadId = track.referenceUploadId,
            )
        val created = createLocked(original, 1, retryOf = id.value).single()
        created
    }

    override suspend fun prepareRecordingReference(id: TrackId): Outcome<RecordingReference> = runtime.operation {
        val account = runtime.requireAccount()
        val owner = runtime.activeAccountId ?: throw ApiFailure(AppError.Unauthorized)
        val current = account.tracks.find { it.id == id }
            ?.takeIf { it.canUseAsReference } ?: throw ApiFailure(AppError.InvalidReference)
        val token = account.token ?: throw ApiFailure(AppError.UnsupportedMode)
        val metadata = runtime.api.listRecordings(token)
            .find { it.id == id.value.removePrefix("recording-") }
            ?: throw ApiFailure(AppError.InvalidReference)
        if (metadata.durationMs !in 3_000..600_000 ||
            metadata.contentType !in setOf("audio/wav", "audio/mp4", "audio/mpeg")
        ) {
            throw ApiFailure(AppError.InvalidReference)
        }
        val uri = metadata.audioUrl
        val canonical = java.net.URI(runtime.api.trackAudioUri(id))
        val source = try {
            java.net.URI(uri)
        } catch (_: java.net.URISyntaxException) {
            throw ApiFailure(AppError.InvalidReference)
        }
        if (source != canonical || current.audioUri != uri || runtime.activeAccountId != owner) {
            throw ApiFailure(AppError.InvalidReference)
        }
        val remote = runtime.recordingDrafts.track(metadata, account)
        if (!remote.canUseAsReference) throw ApiFailure(AppError.InvalidReference)
        RecordingReference(id, owner, uri, remote.title)
    }

    override fun isRecordingReferenceCurrent(reference: RecordingReference): Boolean {
        if (runtime.activeAccountId != reference.ownerId) return false
        val current = runtime.state.value?.tracks?.find { it.id == reference.id } ?: return false
        return current.canUseAsReference && current.audioUri == reference.uri
    }

    override suspend fun saveRecording(localUri: String, title: String, duration: Duration): Outcome<Track> =
        runtime.operation {
            val account = runtime.requireAccount()
            if (account.token != null) {
                val saved = runtime.recordingDrafts.save(account, localUri, title, duration)
                runtime.save(
                    account.copy(
                        tracks =
                            listOf(saved.track) + account.tracks.filterNot { it.id == saved.track.id },
                    ),
                )
                runtime.guarded { runtime.recordingDrafts.acknowledge(listOf(saved)) }
                return@operation saved.track
            }
            val id = "recording-" + UUID.randomUUID()
            val audioUri = runtime.recordings.copyToPrivateStorage(localUri, id)
            val track = Track(
                TrackId(id),
                title.trim().take(80).ifBlank {
                    "Recording"
                },
                account.session.user.displayName.orEmpty(),
                "",
                null,
                duration,
                MusicModel.V2_5,
                false,
                CoverSeed(id),
                TrackStatus.Ready,
                runtime.clock.now(),
                false,
                audioUri = audioUri,
            )
            try {
                runtime.save(account.copy(tracks = listOf(track) + account.tracks))
                track
            } catch (error: Exception) {
                // A cancelled suspend return can follow a committed insert. Preserve referenced audio.
                withContext(NonCancellable) {
                    val persisted = runCatching { runtime.store.readAccount(account.session.user.id.value) }.getOrNull()
                    if (persisted != null && persisted.tracks.none { it.id == track.id && it.audioUri == audioUri }) {
                        runtime.recordings.delete(track, account.session.user.id.value)
                    }
                }
                throw error
            }
        }

    override suspend fun rename(id: TrackId, title: String): Outcome<Unit> = runtime.operation {
        val trimmed = title.trim()
        if (trimmed.length !in 1..80) throw ApiFailure(AppError.InvalidPrompt(null))
        val account = runtime.requireAccount()
        if (account.tracks.none { it.id == id }) throw ApiFailure(AppError.Unknown)
        val target = account.tracks.first { it.id == id }
        if (account.token != null) {
            if (target.status != TrackStatus.Ready && target.status !is TrackStatus.Failed) {
                throw ApiFailure(AppError.Unknown)
            }
            runtime.api.renameTrack(account.token, id, trimmed)
        }
        runtime.save(account.copy(tracks = account.tracks.map { if (it.id == id) it.copy(title = trimmed) else it }))
    }

    override suspend fun delete(id: TrackId): Outcome<Unit> = runtime.operation {
        val account = runtime.requireAccount()
        if (account.token != null) {
            runtime.api.deleteTrack(account.token, id)
        }
        runtime.recordingDrafts.discard(account.session.user.id.value, id)
        account.tracks.find { it.id == id }?.let { runtime.recordings.delete(it, account.session.user.id.value) }
        runtime.recordings.deleteCached(id, account.session.user.id.value)
        runtime.save(
            account.copy(
                tracks = account.tracks.filterNot {
                    it.id == id
                },
                playlists = account.playlists.map {
                    it.copy(
                        tracks = it.tracks.filterNot { track ->
                            track.id == id
                        },
                    )
                },
            ),
            removedTracks = setOf(id.value),
        )
    }

    override suspend fun setLiked(id: TrackId, liked: Boolean): Outcome<Unit> = runtime.operation {
        val account = runtime.requireAccount()
        if (account.tracks.none { it.id == id } && runtime.publicCatalogTracks.value.none { it.id == id }) {
            throw ApiFailure(AppError.Unknown)
        }
        if (account.token != null && !id.value.startsWith("recording-")) {
            runtime.api.setLiked(account.token, id, liked)
        }
        runtime.save(
            account.copy(
                tracks = account.tracks.map { if (it.id == id) it.copy(isLiked = liked) else it },
                likes = if (liked) account.likes + id.value else account.likes - id.value,
            ),
        )
    }
}
