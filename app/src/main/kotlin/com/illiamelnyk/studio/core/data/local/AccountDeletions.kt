package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.notification.GenerationNotifications
import com.illiamelnyk.studio.core.domain.track.TrackId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
internal data class DeletionAudio(val id: String, val uri: String?)

internal data class AccountDeletionIntent(
    val ownerId: String,
    val token: String?,
    val audio: List<DeletionAudio>,
    val references: List<String>,
    val remoteConfirmed: Boolean,
    val localPurged: Boolean = false,
    val remoteCompletionUnverified: Boolean = false,
    val requiresReauthentication: Boolean = false,
)

internal data class AccountDeletionResult(
    val ownerId: String,
    val remoteError: AppError?,
    val remoteCompletionUnverified: Boolean = false,
    val requiresReauthentication: Boolean = false,
)

internal class AccountDeletions(
    private val store: StudioStore,
    private val api: StudioApi,
    private val recordings: RecordingFiles,
    private val notifications: GenerationNotifications = GenerationNotifications.None,
    private val recordingDrafts: RecordingDrafts = RecordingDrafts(store, recordings, api),
) {
    private val intents = AtomicReference<Map<String, AccountDeletionIntent>>(emptyMap())
    private val loadMutex = Mutex()

    suspend fun load() = loadMutex.withLock {
        intents.set(store.pendingDeletions().associateBy { it.ownerId })
    }

    fun pending(): List<AccountDeletionIntent> = intents.get().values.toList()

    suspend fun begin(account: AccountSnapshot): AccountDeletionIntent {
        load()
        val owner = account.session.user.id.value
        pending().firstOrNull { it.ownerId == owner }?.let { return it }
        val intent = AccountDeletionIntent(
            ownerId = owner,
            token = account.token,
            audio = account.tracks.map {
                DeletionAudio(it.id.value, it.audioUri)
            } + recordingDrafts.pendingAudio(owner),
            references = account.tracks.mapNotNull { store.requestFor(owner, it.id.value)?.referenceAudioUri },
            remoteConfirmed = account.token == null,
        )
        persist(intent)
        return intent
    }

    suspend fun reauthenticated(intent: AccountDeletionIntent): AccountDeletionIntent =
        intent.copy(requiresReauthentication = false).also { persist(it) }

    suspend fun execute(original: AccountDeletionIntent): AccountDeletionResult {
        load()
        var intent = original
        var remoteError: AppError? = if (intent.remoteCompletionUnverified || intent.requiresReauthentication) {
            AppError.Unauthorized
        } else {
            null
        }
        if (!intent.remoteConfirmed && !intent.remoteCompletionUnverified && !intent.requiresReauthentication) {
            val token = intent.token
            if (token == null) {
                remoteError = AppError.Unauthorized
            } else {
                try {
                    try {
                        val profile = api.getProfile(token)
                        if (profile.id != intent.ownerId) throw ApiFailure(AppError.Unauthorized)
                    } catch (failure: ApiFailure) {
                        if (failure.serverCode != "ACCOUNT_DELETING") throw failure
                    }
                    api.deleteAccount(token)
                    intent = intent.copy(remoteConfirmed = true, requiresReauthentication = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: ApiFailure) {
                    val classified = if (failure.error == AppError.Unauthorized &&
                        failure.serverCode !in setOf("FIREBASE_IDENTITY_MISSING", "FIREBASE_REAUTH_REQUIRED")
                    ) {
                        try {
                            api.confirmFirebaseIdentity(intent.token)
                            failure
                        } catch (identity: ApiFailure) {
                            identity
                        }
                    } else {
                        failure
                    }
                    remoteError = classified.error
                    intent = when (classified.serverCode) {
                        "FIREBASE_IDENTITY_MISSING" -> intent.copy(remoteCompletionUnverified = true)
                        "FIREBASE_REAUTH_REQUIRED" -> intent.copy(requiresReauthentication = true)
                        else -> intent
                    }
                } catch (_: Exception) {
                    remoteError = AppError.Unknown
                }
                if (intent.remoteConfirmed || intent.remoteCompletionUnverified || intent.requiresReauthentication) {
                    persist(intent)
                }
            }
        }
        if (!intent.localPurged) {
            intent.audio.forEach { recordings.delete(TrackId(it.id), it.uri, intent.ownerId) }
            recordings.deleteRecordingStaging(intent.ownerId)
            recordings.deleteReferences(intent.ownerId, intent.references)
            recordings.deletePlaybackState(intent.ownerId)
            notifications.forget(intent.ownerId)
            store.purgeAccount(intent)
            if (intent.remoteConfirmed) {
                intents.updateAndGet { it - intent.ownerId }
            } else {
                intents.updateAndGet {
                    val purged = intent.copy(audio = emptyList(), references = emptyList(), localPurged = true)
                    it + (intent.ownerId to purged)
                }
            }
        } else if (intent.remoteConfirmed) {
            store.deleteDeletion(intent.ownerId)
            intents.updateAndGet { it - intent.ownerId }
        }
        return AccountDeletionResult(
            intent.ownerId,
            remoteError,
            intent.remoteCompletionUnverified,
            intent.requiresReauthentication,
        )
    }

    private suspend fun persist(intent: AccountDeletionIntent) {
        store.saveDeletion(intent)
        intents.updateAndGet { it + (intent.ownerId to intent) }
    }
}
