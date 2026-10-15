package com.illiamelnyk.studio.core.data.local

import com.illiamelnyk.studio.core.domain.credits.Credits
import com.illiamelnyk.studio.core.domain.playlist.Playlist
import com.illiamelnyk.studio.core.domain.session.Onboarding
import com.illiamelnyk.studio.core.domain.session.Session
import com.illiamelnyk.studio.core.domain.session.SessionDataMode
import com.illiamelnyk.studio.core.domain.session.SignInMethod
import com.illiamelnyk.studio.core.domain.session.User
import com.illiamelnyk.studio.core.domain.session.UserId
import org.json.JSONArray
import org.json.JSONObject

internal object AccountJsonCodec {
    fun decode(json: JSONObject): AccountSnapshot = AccountSnapshot(
        session = json.getJSONObject("profile").toSession(
            dataMode = when {
                json.stringOrNull("token") == null -> SessionDataMode.LocalDemo
                json.stringOrNull("token")?.startsWith("firebase:") == true -> SessionDataMode.FirebaseServer
                else -> SessionDataMode.DevelopmentServer
            },
        ),
        token = json.stringOrNull("token"),
        tracks = json.array("tracks").objects().map { it.toTrack() },
        likes = json.array("likes").strings().toSet(),
        playlists = json.array("playlists").objects().map { playlist ->
            Playlist(
                playlist.getString("id"),
                playlist.getString("name"),
                playlist.array("tracks").objects().map { it.toTrack() },
            )
        },
        credits = Credits(json.optInt("remaining", 25), json.optInt("limit", 25)),
        creditDay = json.optString("day", utcDay()),
    )

    fun encode(account: AccountSnapshot): JSONObject = JSONObject().apply {
        put("profile", account.session.user.toJson())
        put("token", account.token)
        put("tracks", JSONArray(account.tracks.map { it.toJson() }))
        put("likes", JSONArray(account.likes.toList()))
        put(
            "playlists",
            JSONArray(
                account.playlists.map { playlist ->
                    JSONObject().put("id", playlist.id).put("name", playlist.name)
                        .put("tracks", JSONArray(playlist.tracks.map { it.toJson() }))
                },
            ),
        )
        put("remaining", account.credits.remaining)
        put("limit", account.credits.dailyLimit)
        put("day", account.creditDay)
    }

    private fun User.toJson(): JSONObject = JSONObject().apply {
        put("id", id.value)
        put("email", email)
        put("emailVerified", emailVerified)
        put("displayName", displayName)
        put("signInProvider", signInMethod.name)
        put("ageConfirmed", ageConfirmed)
        put("termsVersion", acceptedTermsVersion)
        put("marketingOptIn", marketingOptIn)
    }
}

internal fun JSONObject.toSession(dataMode: SessionDataMode = SessionDataMode.DevelopmentServer): Session = Session(
    dataMode = dataMode,
    user = User(
        id = UserId(getString("id")),
        email = stringOrNull("email"),
        emailVerified = optBoolean("emailVerified"),
        signInMethod = if (optString(
                "signInProvider",
            ).equals("Email", true)
        ) {
            SignInMethod.Email
        } else {
            SignInMethod.Google
        },
        displayName = stringOrNull("displayName"),
        ageConfirmed = optBoolean("ageConfirmed"),
        acceptedTermsVersion = stringOrNull("termsVersion"),
        marketingOptIn = optBoolean("marketingOptIn"),
    ),
    onboarding = Onboarding(emptySet(), optJSONObject("onboarding")?.optString("termsVersion") ?: "2026-10-14"),
)
