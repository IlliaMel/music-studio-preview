package com.illiamelnyk.studio.core.data.local

import android.content.Context
import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.credits.CreditsRepository
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.playback.PlaybackMetadataSnapshot
import com.illiamelnyk.studio.core.domain.playlist.PlaylistRepository
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.track.GenerationWorkScheduler
import com.illiamelnyk.studio.core.domain.track.Track
import com.illiamelnyk.studio.core.domain.track.TrackRepository
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

/** Test fixture composing the real focused repositories; never shipped with the application. */
internal class DurableStudioRepository private constructor(
    private val runtime: StudioAccountRuntime,
    internal val trackData: DurableTrackRepository,
    internal val playlistData: DurablePlaylistRepository,
    internal val sessionData: DurableSessionRepository,
) : TrackRepository by trackData,
    PlaylistRepository by playlistData,
    SessionRepository by sessionData,
    CreditsRepository {
    constructor(
        context: Context,
        store: StudioStore,
        api: StudioApi,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        generationWork: GenerationWorkScheduler = GenerationWorkScheduler.None,
        notifications: GenerationNotifications = GenerationNotifications.None,
        clock: Clock = Clock.System,
    ) : this(StudioAccountRuntime(context, store, api, dispatcher, generationWork, notifications, clock), scope)

    private constructor(runtime: StudioAccountRuntime, scope: CoroutineScope) : this(
        runtime,
        DurableTrackRepository(runtime),
        DurablePlaylistRepository(runtime),
        DurableSessionRepository(runtime),
    ) {
        runBlocking(Dispatchers.IO) { runtime.initialize() }
        runtime.sync.start(scope)
    }

    val references: StudioReferenceAccess get() = runtime
    val catalog: StudioCatalogAccess get() = runtime
    val authentication: StudioAuthentication get() = sessionData
    val synchronization: StudioSynchronization get() = runtime.sync
    val tracks: TrackRepository get() = trackData
    val playlists: PlaylistRepository get() = playlistData
    val sessions: SessionRepository get() = sessionData
    val creditBalance: CreditsRepository = runtime.creditData
    override val credits: Flow<Credits> get() = creditBalance.credits
    val activeAccountId: String? get() = runtime.activeAccountId
    val playbackMetadata get() = runtime.playbackMetadata
    fun isPlaybackMetadataCurrent(snapshot: PlaybackMetadataSnapshot) = runtime.isPlaybackMetadataCurrent(snapshot)
    suspend fun signInDemo() = sessionData.signInDemo()
    suspend fun signInServer() = sessionData.signInServer()
    suspend fun signInFirebase(firebaseUid: String) = sessionData.signInFirebase(firebaseUid)
    fun isFirebaseSessionActive(firebaseUid: String) = sessionData.isFirebaseSessionActive(firebaseUid)
    fun needsFirebaseDeletionCredentials(firebaseUid: String) =
        sessionData.needsFirebaseDeletionCredentials(firebaseUid)
    suspend fun prepareFirebaseSignIn(currentFirebaseUid: String?) =
        sessionData.prepareFirebaseSignIn(currentFirebaseUid)
    suspend fun completeFirebaseSignIn(firebaseUid: String) = sessionData.completeFirebaseSignIn(firebaseUid)
    fun referenceDirectory() = runtime.referenceDirectory()
    internal fun registerCatalogTracks(tracks: List<Track>) = runtime.registerCatalogTracks(tracks)
    fun catalogTrack(track: Track) = runtime.catalogTrack(track)
    suspend fun syncPending(ownerId: String) = runtime.syncPending(ownerId)
    suspend fun exportHeaders(track: Track) = runtime.exportHeaders(track)
    suspend fun playbackHeaders(track: Track) = runtime.playbackHeaders(track)
    internal suspend fun fetchCatalog() = runtime.fetchCatalog()

    /** After advancing the test scheduler, await Room work already holding the owner transaction. */
    internal suspend fun awaitCurrentOperation() {
        runtime.mutex.withLock { }
    }
}
