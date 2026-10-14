package com.illiamelnyk.studio.feature.auth.presentation.onboarding

import androidx.compose.runtime.Immutable
import com.illiamelnyk.studio.core.domain.session.DisplayName
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.domain.session.ProfileUpdate
import com.illiamelnyk.studio.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * The steps a signed-in account still misses, on one screen: a public name, the age and the
 * terms. Only the [missing] ones show. Once the server has them all, the app moves on by itself.
 *
 * Until the kit has a text field, the name can only be the one the sign-in provider suggests.
 */
@Immutable
data class OnboardingState(
    val missing: ImmutableSet<OnboardingStep> = persistentSetOf(),
    val suggestedName: String? = null,
    val useSuggestedName: Boolean = false,
    val ageConfirmed: Boolean = false,
    val termsAccepted: Boolean = false,
    val marketingOptIn: Boolean = false,
    val isSaving: Boolean = false,
    val notice: UiText? = null,
) {
    /** The name to save: the suggestion, if the user took it and it is a valid name. */
    val chosenName: DisplayName?
        get() = suggestedName
            ?.takeIf { useSuggestedName }
            ?.let { (DisplayName.parse(it) as? DisplayName.Parsed.Valid)?.name }

    /** Every missing step is answered. Nothing missing means nothing to send. */
    val canContinue: Boolean
        get() = missing.isNotEmpty() &&
            (OnboardingStep.DisplayName !in missing || chosenName != null) &&
            (OnboardingStep.Age !in missing || ageConfirmed) &&
            (OnboardingStep.Terms !in missing || termsAccepted)
}

/**
 * The answers to the [OnboardingState.missing] steps as one profile update: steps that aren't
 * missing are left alone. The marketing choice is asked with the name, so it goes with it.
 */
fun OnboardingState.toProfileUpdate(currentTermsVersion: String): ProfileUpdate {
    val askedName = OnboardingStep.DisplayName in missing
    return ProfileUpdate(
        displayName = chosenName.takeIf { askedName },
        confirmAge = OnboardingStep.Age in missing && ageConfirmed,
        acceptedTermsVersion = currentTermsVersion.takeIf { OnboardingStep.Terms in missing && termsAccepted },
        marketingOptIn = marketingOptIn.takeIf { askedName },
    )
}

sealed interface OnboardingIntent {
    data object SuggestedNameToggled : OnboardingIntent

    data object AgeToggled : OnboardingIntent

    data object TermsToggled : OnboardingIntent

    data object MarketingToggled : OnboardingIntent

    data object ContinueClicked : OnboardingIntent

    /** Sign in with another account instead. */
    data object SignOutClicked : OnboardingIntent
}
