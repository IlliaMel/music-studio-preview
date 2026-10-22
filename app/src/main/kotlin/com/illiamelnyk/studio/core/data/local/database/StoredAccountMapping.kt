package com.illiamelnyk.studio.core.data.local.database

import com.illiamelnyk.studio.core.data.local.AccountDeletionIntent
import com.illiamelnyk.studio.core.data.local.AccountSnapshot
import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.OnboardingStep
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId

internal fun AccountSnapshot.accountEntity(): StoredAccountEntity {
    val user = session.user
    return StoredAccountEntity(
        ownerId = user.id.value, email = user.email, emailVerified = user.emailVerified,
        signInMethod = user.signInMethod.name, displayName = user.displayName, ageConfirmed = user.ageConfirmed,
        acceptedTermsVersion = user.acceptedTermsVersion, marketingOptIn = user.marketingOptIn,
        missingOnboarding = session.onboarding.missing.map { it.name },
        currentTermsVersion = session.onboarding.currentTermsVersion,
        suggestedDisplayName = session.suggestedDisplayName, dataMode = session.dataMode.name,
        tokenReference = token, remainingCredits = credits.remaining, dailyLimit = credits.dailyLimit,
        creditDay = creditDay, requiresAuthentication = requiresAuthentication,
        canGenerate = user.canGenerate,
    )
}

internal fun StoredAccountRelations.snapshot(): AccountSnapshot {
    val header = account
    return AccountSnapshot(
        session = Session(
            user = User(
                UserId(header.ownerId),
                header.email,
                header.emailVerified,
                SignInMethod.valueOf(header.signInMethod),
                header.displayName,
                header.ageConfirmed,
                header.acceptedTermsVersion,
                header.marketingOptIn,
                header.canGenerate,
            ),
            onboarding = Onboarding(
                header.missingOnboarding.map(OnboardingStep::valueOf).toSet(),
                header.currentTermsVersion,
            ),
            suggestedDisplayName = header.suggestedDisplayName,
            dataMode = SessionDataMode.valueOf(header.dataMode),
        ),
        token = header.tokenReference,
        tracks = tracks.sortedBy { it.position }.map { it.fields.track(it.trackId) },
        playlists = playlists.sortedBy { it.position }.map { playlist ->
            Playlist(
                playlist.playlistId,
                playlist.name,
                members.filter { it.playlistId == playlist.playlistId }.sortedBy { it.position }
                    .map { it.fields.track(it.trackId) },
            )
        },
        likes = likes.sortedBy { it.position }.map { it.trackId }.toSet(),
        credits = Credits(header.remainingCredits, header.dailyLimit),
        creditDay = header.creditDay,
        requiresAuthentication = header.requiresAuthentication,
    )
}

internal fun AccountDeletionIntent.entity() = DeletionIntentEntity(
    ownerId,
    token,
    audio,
    references,
    remoteConfirmed,
    localPurged,
    remoteCompletionUnverified,
    requiresReauthentication,
)

internal fun DeletionIntentEntity.intent() = AccountDeletionIntent(
    ownerId,
    token,
    audio,
    references,
    remoteConfirmed,
    localPurged,
    remoteCompletionUnverified,
    requiresReauthentication,
)
