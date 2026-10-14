package com.illiamelnyk.studio.core.domain.session

/**
 * Changes to the profile, sent as `PATCH /v1/me`: only the fields that are set change, and at
 * least one has to be.
 *
 * @param confirmAge confirms being 18 or older. There is no way to take it back, so `false` means
 *   "no change", not "not an adult".
 * @param acceptedTermsVersion the version of the terms the user has just accepted:
 *   [Onboarding.currentTermsVersion].
 */
data class ProfileUpdate(
    val displayName: DisplayName? = null,
    val confirmAge: Boolean = false,
    val acceptedTermsVersion: String? = null,
    val marketingOptIn: Boolean? = null,
) {
    init {
        require(displayName != null || confirmAge || acceptedTermsVersion != null || marketingOptIn != null) {
            "A profile update needs at least one change"
        }
    }
}
