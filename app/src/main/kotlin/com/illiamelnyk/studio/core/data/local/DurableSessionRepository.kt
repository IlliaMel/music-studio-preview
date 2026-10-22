package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.data.network.ProfileUpdateDto
import com.illiamelnyk.studio.core.data.network.toSession
import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Account-scoped SessionRepository implementation. All mutations share the owner transaction boundary. */
internal class DurableSessionRepository(private val runtime: StudioAccountRuntime) :
    SessionRepository,
    StudioAuthentication {
    override val session: Flow<Session?> = runtime.state.map { it?.session }.distinctUntilChanged()
    override suspend fun signInDemo(): Outcome<Unit> = runtime.operation {
        val id = "local-demo"
        if (runtime.deletions.pending().any { it.ownerId == id }) throw ApiFailure(AppError.Unauthorized)
        val account = runtime.loadAccount(id) ?: AccountSnapshot(
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
        runtime.save(account)
    }

    override suspend fun signInServer(): Outcome<Unit> = runtime.operation {
        if (runtime.api.configuration.authMode != ApiAuthMode.Development ||
            !runtime.api.configuration.debug
        ) {
            throw ApiFailure(AppError.UnsupportedMode)
        }
        val token = "dev:local-developer"
        val profile = runtime.api.getProfile(token)
        var remote = profile.toSession()
        if (runtime.deletions.pending().any {
                it.ownerId == remote.user.id.value
            }
        ) {
            throw ApiFailure(AppError.Unauthorized)
        }
        if (remote.user.displayName == null) {
            remote = runtime.api.updateProfile(
                token,
                ProfileUpdateDto(displayName = "Developer"),
            ).toSession()
        }
        remote = runtime.api.updateProfile(token, ProfileUpdateDto(acceptedTermsVersion = TERMS)).toSession()
        if (runtime.deletions.pending().any {
                it.ownerId == remote.user.id.value
            }
        ) {
            throw ApiFailure(AppError.Unauthorized)
        }
        val quota = runtime.api.getQuota(token)
        val cached = runtime.loadAccount(remote.user.id.value)
        runtime.sync.requestInitialRefresh(remote.user.id.value)
        runtime.save(
            (
                cached ?: AccountSnapshot(
                    remote,
                    token,
                )
                ).copy(
                session = remote,
                token = token,
                requiresAuthentication = false,
                credits = Credits(quota.remaining.coerceIn(0, quota.limit), quota.limit),
                creditDay = utcDay(),
            ),
        )
        runtime.guarded { runtime.refresh() }
    }

    override suspend fun signInFirebase(firebaseUid: String): Outcome<Unit> = runtime.operation {
        if (runtime.api.configuration.authMode != ApiAuthMode.Firebase ||
            firebaseUid.isBlank()
        ) {
            throw ApiFailure(AppError.Unauthorized)
        }
        val reference = "firebase:" + firebaseUid
        val profile = runtime.api.getProfile(reference).toSession(SessionDataMode.FirebaseServer)
        val ownerId = profile.user.id.value
        if (runtime.deletions.pending().any { it.ownerId == ownerId }) throw ApiFailure(AppError.Unauthorized)
        val accepted = runtime.api.updateProfile(reference, ProfileUpdateDto(acceptedTermsVersion = TERMS))
            .toSession(SessionDataMode.FirebaseServer)
        if (accepted.user.id.value != ownerId) throw ApiFailure(AppError.Unauthorized)
        val quota = runtime.api.getQuota(reference)
        val cached = runtime.loadAccount(ownerId)
        runtime.sync.requestInitialRefresh(ownerId)
        runtime.save(
            (
                cached ?: AccountSnapshot(
                    accepted,
                    reference,
                )
                ).copy(
                session = accepted,
                token = reference,
                requiresAuthentication = false,
                credits = Credits(quota.remaining.coerceIn(0, quota.limit), quota.limit),
                creditDay = utcDay(),
            ),
        )
        runtime.guarded { runtime.refresh() }
    }

    override fun isFirebaseSessionActive(firebaseUid: String): Boolean =
        runtime.activeAccountId != null && runtime.state.value?.requiresAuthentication == false &&
            runtime.state.value?.token == "firebase:" + firebaseUid

    override fun needsFirebaseDeletionCredentials(firebaseUid: String): Boolean = runtime.deletions.pending().any {
        it.token == "firebase:" + firebaseUid && !it.remoteConfirmed &&
            !it.remoteCompletionUnverified && !it.requiresReauthentication
    }

    override suspend fun prepareFirebaseSignIn(currentFirebaseUid: String?): Outcome<Unit> = runtime.operation {
        if (currentFirebaseUid != null) {
            runtime.deletions.pending().filter {
                it.token == "firebase:" + currentFirebaseUid &&
                    !(it.localPurged && (it.remoteCompletionUnverified || it.requiresReauthentication))
            }.forEach { intent ->
                val result = runtime.executeDeletion(intent)
                if (result.remoteCompletionUnverified || result.requiresReauthentication) {
                    runtime.api.clearClientSession(intent.token)
                } else {
                    result.remoteError?.let { throw ApiFailure(it) }
                    runtime.api.clearClientSession(intent.token)
                }
            }
        }
    }

    override suspend fun completeFirebaseSignIn(firebaseUid: String): Outcome<Unit> {
        val prepared = runtime.operation {
            val pending = runtime.deletions.pending().filter { it.token == "firebase:" + firebaseUid }
            if (pending.isNotEmpty()) {
                pending.forEach { intent ->
                    val result = runtime.executeDeletion(runtime.deletions.reauthenticated(intent))
                    if (result.remoteError == null || result.remoteCompletionUnverified ||
                        result.requiresReauthentication
                    ) {
                        runtime.api.clearClientSession(intent.token)
                    }
                    result.remoteError?.let { throw ApiFailure(it) }
                }
                throw ApiFailure(AppError.Unauthorized)
            }
            Unit
        }
        return if (prepared is Outcome.Failure) prepared else signInFirebase(firebaseUid)
    }

    override suspend fun updateProfile(update: ProfileUpdate): Outcome<Session> = runtime.operation {
        val account = runtime.requireAccount()
        val updated = if (account.token != null) {
            runtime.api.updateProfile(
                account.token,
                ProfileUpdateDto(
                    displayName = update.displayName?.value,
                    ageConfirmed = true.takeIf { update.confirmAge },
                    acceptedTermsVersion = update.acceptedTermsVersion,
                    marketingOptIn = update.marketingOptIn,
                ),
            ).toSession(account.session.dataMode)
        } else {
            account.session.copy(
                user = account.session.user.copy(
                    displayName = update.displayName?.value ?: account.session.user.displayName,
                    marketingOptIn = update.marketingOptIn ?: account.session.user.marketingOptIn,
                ),
            )
        }
        runtime.save(account.copy(session = updated, requiresAuthentication = false))
        updated
    }

    override suspend fun deleteAccount(): Outcome<Unit> = runtime.operation {
        val account = runtime.state.value
        val intent = if (account == null) {
            runtime.deletions.pending().firstOrNull() ?: throw ApiFailure(AppError.Unauthorized)
        } else {
            val previousGate = runtime.deletingAccountId
            val owner = account.session.user.id.value
            // Close Main-thread reference access before persisting the durable intent on IO.
            runtime.deletingAccountId = owner
            try {
                runtime.deletions.begin(account)
            } catch (error: Exception) {
                // A failed/ambiguous write stays fenced unless rollback is confirmed.
                if (!runtime.deletions.pending().any { it.ownerId == owner }) runtime.deletingAccountId = previousGate
                throw error
            }
        }
        runtime.deletingAccountId = intent.ownerId
        runtime.reconcileGenerationWork()
        runtime.recordNotifications()
        var clearIdentity = false
        try {
            val result = runtime.executeDeletion(intent)
            clearIdentity = result.remoteError == null || result.remoteCompletionUnverified ||
                result.requiresReauthentication
            result.remoteError?.let { throw ApiFailure(it) }
        } finally {
            if (runtime.state.value?.session?.user?.id?.value != intent.ownerId) {
                runtime.deletingAccountId = null
                if (clearIdentity) runtime.api.clearClientSession(intent.token) else runtime.api.forgetCredentials()
            }
        }
    }

    override suspend fun signOut() {
        signOutCurrent(null, checkOwner = false)
    }

    override suspend fun signOutIfCurrent(expectedOwner: UserId?): Boolean =
        signOutCurrent(expectedOwner, checkOwner = true)

    private suspend fun signOutCurrent(expectedOwner: UserId?, checkOwner: Boolean): Boolean =
        withContext(runtime.dispatcher) {
            runtime.initialize()
            runtime.mutex.withLock {
                runtime.refreshStorageStateLocked()
                if (checkOwner && runtime.state.value?.session?.user?.id != expectedOwner) return@withLock false
                val token = runtime.state.value?.token
                runtime.signOutActive(token)
                true
            }
        }

    private companion object {
        const val TERMS = "2026-10-14"
    }
}
