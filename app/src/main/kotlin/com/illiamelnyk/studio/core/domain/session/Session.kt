package com.illiamelnyk.studio.core.domain.session

enum class SessionDataMode { LocalDemo, DevelopmentServer }

/**
 * Someone is signed in: their [user] profile and what is still missing from it before they can
 * use the app.
 *
 * @param suggestedDisplayName the name the sign-in provider knows (the Google account's name),
 *   offered on the name step of onboarding. It never leaves the device by itself.
 */
data class Session(
    val user: User,
    val onboarding: Onboarding,
    val suggestedDisplayName: String? = null,
    val dataMode: SessionDataMode = SessionDataMode.LocalDemo,
)

/**
 * What the user still has to do before using the app, as the server decides it.
 *
 * @param currentTermsVersion the version of the terms to accept now; it changes when the terms do,
 *   and then [OnboardingStep.Terms] comes back for everyone.
 */
data class Onboarding(val missing: Set<OnboardingStep>, val currentTermsVersion: String) {
    val isComplete: Boolean get() = missing.isEmpty()
}

enum class OnboardingStep {
    /** Pick a public name. */
    DisplayName,

    /** Confirm being 18 or older. */
    Age,

    /** Accept the current terms. */
    Terms,
}
