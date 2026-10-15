package com.illiamelnyk.studio.core.data.local

import android.content.Context
import android.net.Uri
import com.illiamelnyk.studio.core.data.fake.FakeCatalog
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.CoverSeed
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import com.illiamelnyk.studio.core.domain.track.GenerationRequest
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.MusicModel
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackId
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import com.illiamelnyk.studio.core.domain.track.TrackStatus
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DurableStudioRepository(
    context: Context,
    private val store: StudioStore,
    private val api: StudioApi,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val generationWork: GenerationWorkScheduler = GenerationWorkScheduler.None,
    private val notifications: GenerationNotifications = GenerationNotifications.None,
) : TrackRepository,
    PlaylistRepository,
    SessionRepository,
    CreditsRepository {
    private val mutex = Mutex()
    private val publicCatalogTracks = MutableStateFlow(FakeCatalog.tracks)
    private val recordings = RecordingFiles(context)
    private val notificationOutbox = NotificationOutbox(store)
    private val deletions = AccountDeletions(store, api, recordings, notifications)

    @Volatile private var deletingAccountId: String? = null
    private var lastSyncMillis = 0L
    private var lastDeletionRetryMillis = 0L
    private val state = MutableStateFlow(loadActive())
    val activeAccountId: String?
        get() = state.value?.session?.user?.id?.value?.takeUnless { it == deletingAccountId }
    override val session: Flow<Session?> = state.map { it?.session }.distinctUntilChanged()
    override val credits: Flow<Credits> = state.map { it?.credits ?: Credits(25, 25) }.distinctUntilChanged()

    init {
        scope.launch(dispatcher) {
            guarded {
                mutex.withLock {
                    resumeDeletions()
                    reconcileGenerationWork()
                    recordNotifications()
                    notifications.deliver(activeAccountId)
                    reconcileGenerationWork()
                }
            }
            while (true) {
                delay(2_000)
                guarded {
                    mutex.withLock {
                        if (System.currentTimeMillis() - lastDeletionRetryMillis >= 30_000) resumeDeletions()
                        val account = state.value
                        val pending = account?.tracks.orEmpty().any {
                            it.status == TrackStatus.Queued || it.status is TrackStatus.Generating
                        }
                        val syncDue = System.currentTimeMillis() - lastSyncMillis >= 30_000
                        if (account?.token == null || pending || syncDue) {
                            refresh()
                        }
                        if (activeAccountId?.let(notificationOutbox::isDirty) == true) recordNotifications()
                        notifications.deliver(activeAccountId)
                        reconcileGenerationWork()
                    }
                }
            }
        }
    }

    suspend fun signInDemo(): Outcome<Unit> = operation {
        val id = "local-demo"
        if (store.read().has("deletion-" + id)) throw ApiFailure(AppError.Unauthorized)
        val account = loadAccount(id) ?: AccountSnapshot(
            Session(
                User(
                    UserId(id),
                    null,
                    false,
                    SignInMethod.Email,
                    "Demo listener",
                    false,
                    null,
                    false,
                ),
                Onboarding(
                    emptySet(),
                    TERMS,
                ),
            ),
            token = null,
        )
        save(account)
    }

    suspend fun signInServer(): Outcome<Unit> = operation {
        if (api.configuration.authMode != ApiAuthMode.Development ||
            !api.configuration.debug
        ) {
            throw ApiFailure(AppError.UnsupportedMode)
        }
        val token = "dev:illia"
        val profile = api.request("GET", "/v1/me", token)
        var remote = profile.toSession()
        if (store.read().has("deletion-" + remote.user.id.value)) throw ApiFailure(AppError.Unauthorized)
        if (remote.user.displayName == null) {
            remote = api.request(
                "PATCH",
                "/v1/me",
                token,
                JSONObject().put(
                    "displayName",
                    "Illia Melnyk",
                ),
            ).toSession()
        }
        remote = api.request("PATCH", "/v1/me", token, JSONObject().put("acceptedTermsVersion", TERMS)).toSession()
        if (store.read().has("deletion-" + remote.user.id.value)) throw ApiFailure(AppError.Unauthorized)
        val cached = loadAccount(remote.user.id.value)
        save((cached ?: AccountSnapshot(remote, token)).copy(session = remote, token = token))
        guarded { refresh() }
    }

    suspend fun signInFirebase(firebaseUid: String): Outcome<Unit> = operation {
        if (api.configuration.authMode != ApiAuthMode.Firebase ||
            firebaseUid.isBlank()
        ) {
            throw ApiFailure(AppError.Unauthorized)
        }
        val reference = "firebase:" + firebaseUid
        val profile = api.request("GET", "/v1/me", reference).toSession(SessionDataMode.FirebaseServer)
        val ownerId = profile.user.id.value
        if (store.read().has("deletion-" + ownerId)) throw ApiFailure(AppError.Unauthorized)
        val accepted = api.request("PATCH", "/v1/me", reference, JSONObject().put("acceptedTermsVersion", TERMS))
            .toSession(SessionDataMode.FirebaseServer)
        if (accepted.user.id.value != ownerId) throw ApiFailure(AppError.Unauthorized)
        val cached = loadAccount(ownerId)
        save((cached ?: AccountSnapshot(accepted, reference)).copy(session = accepted, token = reference))
        guarded { refresh() }
    }

    fun referenceDirectory(): java.io.File {
        val id = requireAccount().session.user.id.value
        if (id == deletingAccountId) throw ApiFailure(AppError.Unauthorized)
        return recordings.referenceDirectory(id)
    }

    internal fun registerCatalogTracks(tracks: List<Track>) {
        publicCatalogTracks.value =
            tracks.distinctBy { it.id }.map { it.copy(isLiked = false, audioHeaders = emptyMap()) }
    }

    fun catalogTrack(track: Track): Track {
        val account = state.value ?: return track.copy(isLiked = false, audioHeaders = emptyMap())
        val liked = track.id.value in account.likes
        if (account.token == null) return track.copy(isLiked = liked, audioHeaders = emptyMap())
        return track.copy(
            isLiked = liked,
            audioUri = api.baseUrl + "/v1/catalog/tracks/" + track.id.value + "/audio",
            audioHeaders = authHeaders(account),
        )
    }

    override fun observeMine(): Flow<List<Track>> = state.map { it?.tracks.orEmpty() }.distinctUntilChanged()
    override fun observeLiked(): Flow<List<Track>> = combine(state, publicCatalogTracks) { account, catalog ->
        account?.tracks.orEmpty().filter {
            it.isLiked
        } + catalog.filter {
            it.id.value in account?.likes.orEmpty()
        }.map {
            catalogTrack(it).copy(isLiked = true)
        }
    }.distinctUntilChanged()
    override fun observeById(id: TrackId): Flow<Track?> = combine(state, publicCatalogTracks) { account, catalog ->
        account?.tracks?.find {
            it.id == id
        } ?: catalog.find {
            it.id == id
        }?.let {
            catalogTrack(it).copy(isLiked = it.id.value in account?.likes.orEmpty())
        }
    }.distinctUntilChanged()
    override fun observeAll(): Flow<List<Playlist>> = combine(state, publicCatalogTracks) { account, catalog ->
        account?.playlists.orEmpty().map { playlist ->
            playlist.copy(
                tracks = playlist.tracks.map { track ->
                    account?.tracks?.find {
                        it.id == track.id
                    } ?: catalog.find { it.id == track.id }?.let(::catalogTrack) ?: track
                },
            )
        }
    }.distinctUntilChanged()

    override suspend fun create(request: GenerationRequest, variants: Int): Outcome<List<Track>> =
        operation { createLocked(request, variants) }

    private suspend fun createLocked(request: GenerationRequest, variants: Int): List<Track> {
        if (variants !in 1..2 ||
            request.prompt.trim().length !in 10..2000 ||
            (request.customLyrics?.length ?: 0) > 10000 ||
            (request.instrumental && request.customLyrics != null)
        ) {
            throw ApiFailure(AppError.InvalidPrompt(null))
        }
        val account = requireAccount()
        request.remixTrackId?.let { sourceId ->
            val source = account.tracks.find { it.id.value == sourceId }
                ?: publicCatalogTracks.value.find { it.id.value == sourceId }
                ?: throw ApiFailure(AppError.InvalidPrompt(null))
            if (source.remixTrackId != null || source.status != TrackStatus.Ready) {
                throw ApiFailure(AppError.InvalidPrompt(null))
            }
        }

        val batch = if (account.token != null) {
            api.preflightGeneration(account.token, request, variants)
            var uploaded = request.referenceUploadId
            if (uploaded == null && request.referenceAudioUri != null) {
                val uploadKey = "upload-" + account.session.user.id.value + "-" + request.referenceAudioUri.hashCode()
                uploaded = store.read().stringOrNull(uploadKey) ?: api.upload(
                    request.referenceAudioUri,
                    account.token,
                ).also { id ->
                    store.change {
                        it.put(
                            uploadKey,
                            id,
                        )
                    }
                }
            }
            val body = request.copy(referenceUploadId = uploaded).toJson().apply {
                remove("referenceAudioUri")
                put("variants", variants)
            }
            val signature = body.toString()
            val pending = store.read().optJSONObject("pending-" + account.session.user.id.value)
            val key = if (pending?.optString("signature") == signature) {
                pending.getString("key")
            } else {
                UUID.randomUUID().toString()
            }
            store.change {
                it.put(
                    "pending-" + account.session.user.id.value,
                    JSONObject().put(
                        "signature",
                        signature,
                    ).put(
                        "key",
                        key,
                    ),
                )
            }
            val response = api.request("POST", "/v1/tracks", account.token, body, key)
            val results = response.optJSONArray("tracks")?.objects() ?: listOf(response)
            val tracks = results.map { it.toTrack(account.session.user.displayName.orEmpty(), authHeaders(account)) }
            tracks
        } else {
            val reset = resetCredits(account)
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
                    Clock.System.now(),
                    false,
                    customLyrics = request.customLyrics,
                    generationId = group, variantIndex = index, variants = variants,
                    quality = request.quality,
                    remixTrackId = request.remixTrackId, referenceUploadId = request.referenceUploadId,
                )
            }
        }
        batch.forEach { track -> store.change { root -> root.put("request-" + track.id.value, request.toJson()) } }
        val current = requireAccount()
        val updated = if (account.token == null) {
            resetCredits(current).let {
                it.copy(credits = it.credits.copy(remaining = it.credits.remaining - 1))
            }
        } else {
            current
        }
        val batchIds = batch.map { it.id }.toSet()
        save(updated.copy(tracks = batch + current.tracks.filterNot { it.id in batchIds }))
        if (account.token != null) {
            store.change { it.remove("pending-" + account.session.user.id.value) }
            guarded { updateQuota() }
        }
        return batch
    }

    override suspend fun retry(id: TrackId): Outcome<Track> = operation {
        val account = requireAccount()
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
        val replacementId = store.read().stringOrNull("retry-" + id.value)
        val replacement = account.tracks.find { it.id.value == replacementId }
        if (replacement != null && replacement.status !is TrackStatus.Failed) return@operation replacement
        val original = store.read().optJSONObject("request-" + id.value)?.toRequest()
            ?: GenerationRequest(
                track.prompt,
                track.duration,
                track.model,
                quality = track.quality,
                instrumental = track.instrumental,
                customLyrics = track.customLyrics,
                remixTrackId = track.remixTrackId,
                referenceUploadId = track.referenceUploadId,
            )
        val created = createLocked(original, 1).single()
        store.change { it.put("retry-" + id.value, created.id.value) }
        created
    }

    override suspend fun saveRecording(localUri: String, title: String, duration: Duration): Outcome<Track> =
        operation {
            val account = requireAccount()
            val id = "recording-" + UUID.randomUUID()
            val audioUri = recordings.copyToPrivateStorage(localUri, id)
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
                Clock.System.now(),
                false,
                audioUri = audioUri,
            )
            try {
                save(account.copy(tracks = listOf(track) + account.tracks))
                track
            } catch (error: Exception) {
                recordings.delete(track)
                throw error
            }
        }

    override suspend fun rename(id: TrackId, title: String): Outcome<Unit> = operation {
        val trimmed = title.trim()
        if (trimmed.length !in 1..80) throw ApiFailure(AppError.InvalidPrompt(null))
        val account = requireAccount()
        if (account.tracks.none { it.id == id }) throw ApiFailure(AppError.Unknown)
        val target = account.tracks.first { it.id == id }
        if (account.token != null && !id.value.startsWith("recording-")) {
            if (target.status != TrackStatus.Ready && target.status !is TrackStatus.Failed) {
                throw ApiFailure(AppError.Unknown)
            }
            api.request("PATCH", "/v1/tracks/" + id.value, account.token, JSONObject().put("title", trimmed))
        }
        save(account.copy(tracks = account.tracks.map { if (it.id == id) it.copy(title = trimmed) else it }))
    }

    override suspend fun delete(id: TrackId): Outcome<Unit> = operation {
        val account = requireAccount()
        if (account.token != null && !id.value.startsWith("recording-")) {
            api.request(
                "DELETE",
                "/v1/tracks/" + id.value,
                account.token,
            )
        }
        account.tracks.find { it.id == id }?.let(recordings::delete)
        recordings.deleteCached(id)
        store.change {
            it.remove("request-" + id.value)
            it.remove("rename-" + id.value)
            it.remove("retry-" + id.value)
            it.remove("detail-" + id.value)
        }
        save(
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
        )
    }

    override suspend fun setLiked(id: TrackId, liked: Boolean): Outcome<Unit> = operation {
        val account = requireAccount()
        if (account.tracks.none { it.id == id } && publicCatalogTracks.value.none { it.id == id }) {
            throw ApiFailure(AppError.Unknown)
        }
        if (account.token != null && !id.value.startsWith("recording-")) {
            api.request(if (liked) "PUT" else "DELETE", "/v1/likes/" + id.value, account.token)
        }
        save(
            account.copy(
                tracks = account.tracks.map { if (it.id == id) it.copy(isLiked = liked) else it },
                likes = if (liked) account.likes + id.value else account.likes - id.value,
            ),
        )
    }

    override suspend fun create(name: String): Outcome<Playlist> = operation {
        val account = requireAccount()
        if (name.trim().length !in 1..80) throw ApiFailure(AppError.InvalidPrompt(null))
        val playlist = if (account.token != null) {
            val json = api.request("POST", "/v1/playlists", account.token, JSONObject().put("name", name.trim()))
            Playlist(json.getString("id"), json.getString("name"))
        } else {
            Playlist(UUID.randomUUID().toString(), name.trim())
        }
        save(account.copy(playlists = account.playlists + playlist))
        playlist
    }

    override suspend fun addTrack(playlistId: String, track: Track): Outcome<Unit> =
        editPlaylist(playlistId, track.id, add = true) {
            if (it.tracks.any { member -> member.id == track.id }) it else it.copy(tracks = it.tracks + track)
        }

    override suspend fun removeTrack(playlistId: String, trackId: TrackId): Outcome<Unit> =
        editPlaylist(playlistId, trackId, add = false) {
            it.copy(tracks = it.tracks.filterNot { member -> member.id == trackId })
        }

    private suspend fun editPlaylist(
        id: String,
        trackId: TrackId,
        add: Boolean,
        edit: (Playlist) -> Playlist,
    ): Outcome<Unit> = operation {
        val account = requireAccount()
        if (account.playlists.none { it.id == id }) throw ApiFailure(AppError.Unknown)
        if (account.token != null && !trackId.value.startsWith("recording-")) {
            val path = "/v1/playlists/" + id + "/tracks/" + trackId.value
            api.request(if (add) "PUT" else "DELETE", path, account.token)
        }
        save(account.copy(playlists = account.playlists.map { if (it.id == id) edit(it) else it }))
    }

    override suspend fun clear() {
        operation { save(requireAccount().copy(playlists = emptyList())) }
    }

    override suspend fun updateProfile(update: ProfileUpdate): Outcome<Session> = operation {
        val account = requireAccount()
        val updated = if (account.token != null) {
            api.request(
                "PATCH",
                "/v1/me",
                account.token,
                JSONObject().apply {
                    update.displayName?.let { put("displayName", it.value) }
                    if (update.confirmAge) put("ageConfirmed", true)
                    update.acceptedTermsVersion?.let { put("acceptedTermsVersion", it) }
                    update.marketingOptIn?.let { put("marketingOptIn", it) }
                },
            ).toSession(account.session.dataMode)
        } else {
            account.session.copy(
                user = account.session.user.copy(
                    displayName = update.displayName?.value ?: account.session.user.displayName,
                    marketingOptIn = update.marketingOptIn ?: account.session.user.marketingOptIn,
                ),
            )
        }
        save(account.copy(session = updated))
        updated
    }

    override suspend fun deleteAccount(): Outcome<Unit> = operation {
        val intent = state.value?.let(deletions::begin)
            ?: deletions.pending().firstOrNull() ?: throw ApiFailure(AppError.Unauthorized)
        deletingAccountId = intent.ownerId
        reconcileGenerationWork()
        recordNotifications()
        try {
            val result = deletions.execute(intent)
            if (state.value?.session?.user?.id?.value == result.ownerId) state.value = null
            result.remoteError?.let { throw ApiFailure(it) }
        } finally {
            if (state.value?.session?.user?.id?.value != intent.ownerId) {
                deletingAccountId = null
                api.forgetCredentials()
            }
        }
        Unit
    }

    private suspend fun resumeDeletions() {
        lastDeletionRetryMillis = System.currentTimeMillis()
        deletions.pending().forEach { intent ->
            guarded {
                val result = deletions.execute(intent)
                if (state.value?.session?.user?.id?.value == result.ownerId) state.value = null
                if (deletingAccountId == result.ownerId) deletingAccountId = null
                reconcileGenerationWork()
                recordNotifications()
            }
        }
    }

    override suspend fun signOut() {
        withContext(dispatcher) {
            mutex.withLock {
                store.change {
                    it.remove("active")
                }
                state.value = null
                api.forgetCredentials()
                reconcileGenerationWork()
                recordNotifications()
            }
        }
    }

    suspend fun syncPending(ownerId: String): Outcome<Boolean> = operation {
        val account = state.value
        if (activeAccountId != ownerId) {
            reconcileGenerationWork()
            return@operation false
        }
        recordNotifications()
        if (needsGenerationSync(account)) refresh()
        guarded { notifications.deliver(ownerId) }
        reconcileGenerationWork()
        needsGenerationSync(state.value) || needsNotificationSync(state.value)
    }

    private fun needsNotificationSync(account: AccountSnapshot?): Boolean {
        val owner = activeAccountId ?: return false
        if (account?.token == null || account.session.user.id.value != owner) return false
        return try {
            notificationOutbox.isDirty(owner) || notifications.hasPending(owner)
        } catch (_: Exception) {
            true
        }
    }

    private fun needsGenerationSync(account: AccountSnapshot?): Boolean = account != null &&
        account.token != null && account.session.user.id.value != deletingAccountId &&
        !store.read().has("deletion-" + account.session.user.id.value) &&
        account.tracks.any { it.status == TrackStatus.Queued || it.status is TrackStatus.Generating }

    private fun reconcileGenerationWork() {
        val account = state.value
        val pending = needsGenerationSync(account) || needsNotificationSync(account)
        val owner = account?.session?.user?.id?.value?.takeIf { pending }
        try {
            val active = activeAccountId
            if (owner == null && active != null) generationWork.complete(active) else generationWork.reconcile(owner)
        } catch (error: Exception) {
            android.util.Log.w("StudioData", "Cannot schedule generation sync: " + error.javaClass.simpleName)
        }
    }

    private suspend fun refresh() {
        val account = state.value ?: return
        if (store.read().has("deletion-" + account.session.user.id.value)) return
        lastSyncMillis = System.currentTimeMillis()
        if (account.token == null) {
            val tracks = account.tracks.map { track ->
                if (track.status == TrackStatus.Queued || track.status is TrackStatus.Generating) {
                    val elapsed = (Clock.System.now() - track.createdAt).inWholeMilliseconds
                    val status = when {
                        elapsed >= 4_000 && track.prompt.contains(
                            "in the style of",
                            true,
                        ) ->
                            TrackStatus.Failed(AppError.InvalidPrompt("Original music with a similar mood"))

                        elapsed >= 4_000 -> TrackStatus.Ready

                        else -> TrackStatus.Generating((elapsed / 4_000f).coerceIn(0f, 0.99f))
                    }
                    track.copy(status = status)
                } else {
                    track
                }
            }
            val next = resetCredits(account).copy(tracks = tracks)
            if (next != account) save(next)
        } else {
            val serverLikes = api.request("GET", "/v1/likes", account.token).array("trackIds").strings().toSet()
            val likes = serverLikes + account.likes.filter { it.startsWith("recording-") }
            val remote = mutableListOf<Track>()
            var cursor: String? = null
            do {
                val path = "/v1/tracks?limit=50" + (cursor?.let { "&cursor=" + Uri.encode(it) } ?: "")
                val page = api.request("GET", path, account.token)
                for (json in page.array("tracks").objects()) {
                    var fresh = json.toTrack(account.session.user.displayName.orEmpty(), authHeaders(account))
                    if (fresh.status == TrackStatus.Ready) {
                        val detailKey = "detail-" + fresh.id.value
                        val cachedDetail = store.read().optJSONObject(detailKey)
                        val detail = cachedDetail ?: api.request(
                            "GET",
                            "/v1/tracks/" + fresh.id.value,
                            account.token,
                        ).also { json -> store.change { it.put(detailKey, json) } }
                        val detailed = detail.toTrack(account.session.user.displayName.orEmpty(), authHeaders(account))
                        fresh = fresh.copy(words = detailed.words, customLyrics = detailed.customLyrics)
                    }
                    remote += fresh.copy(
                        isLiked = fresh.id.value in likes,
                    )
                }
                cursor = page.stringOrNull("nextCursor")
            } while (cursor != null)
            val recordings = account.tracks.filter { it.id.value.startsWith("recording-") }
            val order = compareByDescending<Track> { it.createdAt }
                .thenBy { it.generationId }.thenBy { it.variantIndex }
            val allTracks = (remote + recordings).sortedWith(order)
            val serverPlaylists = api.request("GET", "/v1/playlists", account.token).array("playlists").objects()
            val playlists = serverPlaylists.map { json ->
                val id = json.getString("id")
                val members = json.array("trackIds").strings().mapNotNull { trackId ->
                    allTracks.find { it.id.value == trackId }
                        ?: publicCatalogTracks.value.find { it.id.value == trackId }?.let(::catalogTrack)
                }
                val localMembers = account.playlists.find { it.id == id }?.tracks.orEmpty()
                    .filter { it.id.value.startsWith("recording-") }
                Playlist(id, json.getString("name"), members + localMembers)
            }
            save(account.copy(tracks = allTracks, likes = likes, playlists = playlists))
            updateQuota()
        }
    }

    private suspend fun updateQuota() {
        val account = requireAccount()
        val quota = api.request("GET", "/v1/me/quota", account.token ?: return)
        val limit = quota.getInt("limit")
        save(account.copy(credits = Credits(quota.getInt("remaining").coerceIn(0, limit), limit)))
    }

    private fun requireAccount(): AccountSnapshot {
        val account = state.value ?: throw ApiFailure(AppError.Unauthorized)
        if (store.read().has("deletion-" + account.session.user.id.value)) throw ApiFailure(AppError.Unauthorized)
        return account
    }
    private fun resetCredits(account: AccountSnapshot): AccountSnapshot = if (account.creditDay == utcDay()) {
        account
    } else {
        account.copy(
            credits = Credits(
                25,
                25,
            ),
            creditDay = utcDay(),
        )
    }
    private fun authHeaders(account: AccountSnapshot): Map<
        String,
        String,
        > =
        account.token?.let {
            api.audioHeaders(it)
        }.orEmpty()
    private fun save(account: AccountSnapshot) {
        store.change { root ->
            notificationOutbox.register(root, account)
            root.put(
                "active",
                account.session.user.id.value,
            ).put(
                "account-" + account.session.user.id.value,
                AccountJsonCodec.encode(account),
            )
        }
        state.value = account
        reconcileGenerationWork()
        recordNotifications()
    }
    private fun recordNotifications() {
        try {
            val owner = activeAccountId
            if (owner != null) notificationOutbox.markDirty(owner)
            val observed = owner?.let(notificationOutbox::observed).orEmpty()
            notifications.recover(owner, state.value?.tracks.orEmpty(), observed)
            if (owner != null) notificationOutbox.recorded(owner)
        } catch (error: Exception) {
            android.util.Log.w("StudioData", "Cannot record completion: " + error.javaClass.simpleName)
        }
    }

    suspend fun playbackHeaders(track: Track): Map<String, String> = withContext(dispatcher) {
        mutex.withLock {
            val source = track.audioUri?.let { java.net.URI(it) } ?: return@withLock emptyMap()
            if (source.scheme !in setOf("http", "https")) return@withLock emptyMap()
            val origin = java.net.URI(api.baseUrl)
            if (source.scheme != origin.scheme || source.host != origin.host || source.port != origin.port) {
                return@withLock emptyMap()
            }
            if (source.rawUserInfo != null || source.rawQuery != null || source.rawFragment != null) {
                throw ApiFailure(AppError.Unauthorized)
            }
            val account = requireAccount()
            val owner = activeAccountId ?: throw ApiFailure(AppError.Unauthorized)
            val id = Uri.encode(track.id.value)
            val owned = source.rawPath == "/v1/tracks/" + id + "/audio" && account.tracks.any { it.id == track.id }
            val catalog = source.rawPath == "/v1/catalog/tracks/" + id + "/audio"
            if (!owned && !catalog) throw ApiFailure(AppError.Unauthorized)
            val token = account.token ?: return@withLock emptyMap()
            api.authorization(token)
            if (activeAccountId != owner) throw ApiFailure(AppError.Unauthorized)
            api.audioHeaders(token)
        }
    }

    internal suspend fun fetchCatalog(): Outcome<JSONObject> = operation {
        val account = requireAccount()
        api.request("GET", "/v1/catalog", account.token ?: throw ApiFailure(AppError.UnsupportedMode))
    }

    private fun loadActive(): AccountSnapshot? = store.read().stringOrNull("active")?.let(::loadAccount)
        ?.takeIf { (api.isAvailable || it.token == null) && !store.read().has("deletion-" + it.session.user.id.value) }
        ?.let { account ->
            account.copy(tracks = account.tracks.map { it.copy(audioHeaders = authHeaders(account)) })
        }
    private fun loadAccount(id: String): AccountSnapshot? =
        store.read().optJSONObject("account-" + id)?.let(AccountJsonCodec::decode)

    private suspend fun <T> operation(action: suspend () -> T): Outcome<T> = withContext(dispatcher) {
        mutex.withLock {
            try {
                Outcome.Success(action())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ApiFailure) {
                Outcome.Failure(failure.error)
            } catch (error: Exception) {
                android.util.Log.e("StudioData", "Data operation failed: " + error.javaClass.simpleName)
                Outcome.Failure(AppError.Unknown)
            } finally {
                guarded { notifications.deliver(activeAccountId) }
                reconcileGenerationWork()
            }
        }
    }
    private suspend fun guarded(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiFailure) {
            if (error.error == AppError.Unauthorized) {
                android.util.Log.w(
                    "StudioData",
                    "Server session unavailable; cached data retained",
                )
            }
        } catch (error: Exception) {
            android.util.Log.e("StudioData", "Refresh failed: " + error.javaClass.simpleName)
        }
    }
    private companion object {
        const val TERMS = "2026-10-14"
    }
}
