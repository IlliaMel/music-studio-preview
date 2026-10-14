package com.illiamelnyk.studio.core.domain.session

import com.illiamelnyk.studio.core.domain.CoverSeed

@JvmInline
value class UserId(val value: String)

/**
 * The signed-in user's profile, as `GET /v1/me` returns it.
 *
 * @param email `null` when the sign-in provider hides the address.
 * @param displayName the public name; `null` until the user picks one during onboarding.
 * @param acceptedTermsVersion the version of the terms the user accepted last, if any.
 */
data class User(
    val id: UserId,
    val email: String?,
    val emailVerified: Boolean,
    val signInMethod: SignInMethod,
    val displayName: String?,
    val ageConfirmed: Boolean,
    val acceptedTermsVersion: String?,
    val marketingOptIn: Boolean,
) {
    /** The avatar art until there are profile pictures: stable for the account. */
    val avatarSeed: CoverSeed get() = CoverSeed(id.value)
}

enum class SignInMethod {
    Google,

    /** A magic link sent by email. */
    Email,
}
