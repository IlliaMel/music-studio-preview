package com.illiamelnyk.studio.core.data.fake

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.Outcome
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionRepository
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sessions and profiles kept in memory, as the backend keeps them: the app starts signed out, and
 * an account keeps its profile after signing out, so the next sign-in skips onboarding.
 *
 * Temporary: replaced by the backend-backed implementation (Firebase Auth and `/v1/me`).
 */
class FakeSessionRepository : SessionRepository {

    private val accounts = MutableStateFlow<Map<UserId, User>>(emptyMap())
    private val current = MutableStateFlow<Session?>(null)

    override val session: Flow<Session?> = current.asStateFlow()

    /**
     * Signs in as the account [name], the way the backend's development tokens (`dev:<name>`) do:
     * the same name is always the same account. A new account starts with an empty profile.
     *
     * @param suggestedDisplayName what the sign-in provider calls the user.
     */
    fun signIn(name: String, method: SignInMethod, suggestedDisplayName: String?) {
        val id = UserId("dev-$name")
        val user = accounts.value[id] ?: User(
            id = id,
            email = "$name@dev.local",
            emailVerified = true,
            signInMethod = method,
            displayName = null,
            ageConfirmed = false,
            acceptedTermsVersion = null,
            marketingOptIn = false,
        )
        accounts.update { it + (id to user) }
        current.value = Session(user, onboardingOf(user), suggestedDisplayName)
    }

    override suspend fun updateProfile(update: ProfileUpdate): Outcome<Session> {
        delay(FakeNetwork.latency)
        val session = current.value ?: return Outcome.Failure(AppError.Unauthorized)
        // The server accepts only the current terms; an outdated version means the app is behind.
        if (update.acceptedTermsVersion != null && update.acceptedTermsVersion != TERMS_VERSION) {
            return Outcome.Failure(AppError.Unknown)
        }
        val user = session.user.let { user ->
            user.copy(
                displayName = update.displayName?.value ?: user.displayName,
                ageConfirmed = user.ageConfirmed || update.confirmAge,
                acceptedTermsVersion = update.acceptedTermsVersion ?: user.acceptedTermsVersion,
                marketingOptIn = update.marketingOptIn ?: user.marketingOptIn,
            )
        }
        accounts.update { it + (user.id to user) }
        val updated = session.copy(user = user, onboarding = onboardingOf(user))
        current.value = updated
        return Outcome.Success(updated)
    }

    override suspend fun deleteAccount(): Outcome<Unit> {
        delay(FakeNetwork.latency)
        val session = current.value ?: return Outcome.Failure(AppError.Unauthorized)
        accounts.update { it - session.user.id }
        current.value = null
        return Outcome.Success(Unit)
    }

    override suspend fun signOut() {
        current.value = null
    }

    /** The server's rule: a public name, a confirmed age and the current terms. */
    private fun onboardingOf(user: User) = Onboarding(
        missing = buildSet {
            if (user.displayName == null) add(OnboardingStep.DisplayName)
            if (!user.ageConfirmed) add(OnboardingStep.Age)
            if (user.acceptedTermsVersion != TERMS_VERSION) add(OnboardingStep.Terms)
        },
        currentTermsVersion = TERMS_VERSION,
    )

    private companion object {
        /** The backend's `TERMS_VERSION`. */
        const val TERMS_VERSION = "2026-10-14"
    }
}
