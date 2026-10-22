package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.AppError
import com.illiamelnyk.studio.core.domain.track.Track
import kotlinx.coroutines.withContext

/** Resolves fresh transport credentials only after validating current owner and canonical audio source. */
internal class StudioAudioAccess(private val runtime: StudioAccountRuntime) {
    suspend fun exportHeaders(track: Track): Map<String, String> = withContext(runtime.dispatcher) {
        val account = audioAccount(track)
        resolveAudioHeaders(track, account)
    }

    suspend fun playbackHeaders(track: Track): Map<String, String> = withContext(runtime.dispatcher) {
        val source = track.audioUri?.let { java.net.URI(it) } ?: return@withContext emptyMap()
        if (source.scheme !in setOf("http", "https")) return@withContext emptyMap()
        val origin = java.net.URI(runtime.api.baseUrl)
        if (source.scheme != origin.scheme || source.host != origin.host || source.port != origin.port) {
            return@withContext emptyMap()
        }
        resolveAudioHeaders(track, audioAccount(track))
    }

    internal fun audioAccount(track: Track): AccountSnapshot {
        val account = runtime.requireAccount()
        if (runtime.activeAccountId != account.session.user.id.value) throw ApiFailure(AppError.Unauthorized)
        val current = account.tracks.find { it.id == track.id }
        val catalog = runtime.publicCatalogTracks.value.find { it.id == track.id }
        val catalogUri = if (account.token == null) {
            catalog?.audioUri
        } else {
            catalog?.let { runtime.api.catalogAudioUri(it.id) }
        }
        if (current == null && catalog == null) throw ApiFailure(AppError.Unauthorized)
        val expected = if (current != null) current.audioUri else catalogUri
        if (track.audioUri != expected) throw ApiFailure(AppError.Unauthorized)
        if (expected == null) {
            if (account.token != null) throw ApiFailure(AppError.Unauthorized)
            return account
        }
        val source = java.net.URI(expected)
        if (source.scheme in setOf("http", "https")) {
            val expectedUri = if (current != null) {
                runtime.api.trackAudioUri(track.id)
            } else {
                runtime.api.catalogAudioUri(track.id)
            }
            if (source != java.net.URI(expectedUri)) throw ApiFailure(AppError.Unauthorized)
        }
        return account
    }

    internal suspend fun resolveAudioHeaders(track: Track, account: AccountSnapshot): Map<String, String> {
        val uri = track.audioUri ?: return emptyMap()
        if (java.net.URI(uri).scheme !in setOf("http", "https")) return emptyMap()
        val token = account.token ?: return emptyMap()
        val bearer = runtime.api.authorization(token)
        val current = audioAccount(track)
        if (current.session.user.id != account.session.user.id || current.token != token) {
            throw ApiFailure(AppError.Unauthorized)
        }
        return mapOf("Authorization" to "Bearer " + bearer)
    }
}
