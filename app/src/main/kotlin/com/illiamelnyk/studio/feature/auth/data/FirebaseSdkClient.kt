package com.illiamelnyk.studio.feature.auth.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.illiamelnyk.studio.core.data.local.FirebaseBearerToken
import com.illiamelnyk.studio.core.data.local.FirebaseIdentityMissing
import com.illiamelnyk.studio.core.data.local.FirebaseReauthenticationRequired
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class FirebaseSdkClient(private val context: Context, private val configuration: FirebaseConfiguration) :
    FirebaseClient {
    private val signingIn = AtomicBoolean()
    private val epoch = AtomicLong()
    private val authentication: FirebaseAuth? by lazy {
        if (!configuration.isConfigured) {
            null
        } else {
            val options = FirebaseOptions.Builder()
                .setApplicationId(configuration.appId)
                .setApiKey(configuration.apiKey)
                .setProjectId(configuration.projectId)
                .build()
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
                ?: FirebaseApp.initializeApp(context, options, APP_NAME)
            FirebaseAuth.getInstance(app)
        }
    }

    override val currentUid: String? get() = authentication?.currentUser?.uid

    override suspend fun signIn(googleIdToken: String): String = withContext(Dispatchers.IO) {
        val auth = authentication ?: throw IOException("Firebase authentication is not configured")
        check(signingIn.compareAndSet(false, true)) { "Authentication is already in progress" }
        val attempt = epoch.incrementAndGet()
        suspendCancellableCoroutine { continuation ->
            val task = try {
                auth.signInWithCredential(GoogleAuthProvider.getCredential(googleIdToken, null))
            } catch (error: Exception) {
                signingIn.set(false)
                continuation.resumeWithException(error)
                return@suspendCancellableCoroutine
            }
            task.addOnCompleteListener { completed ->
                try {
                    val uid = if (completed.isSuccessful) completed.result?.user?.uid else null
                    if (completed.isCanceled) {
                        if (continuation.isActive) continuation.resumeWithException(CancellationException())
                    } else if (!completed.isSuccessful || uid.isNullOrBlank()) {
                        if (continuation.isActive) {
                            val error = completed.exception
                            continuation.resumeWithException(
                                if (error is FirebaseNetworkException) {
                                    IOException("Authentication unavailable")
                                } else {
                                    error ?: IOException("Authentication failed")
                                },
                            )
                        }
                    } else if (!continuation.isActive) {
                        if (epoch.get() == attempt && auth.currentUser?.uid == uid) auth.signOut()
                    } else {
                        continuation.resume(uid) { _, _, _ ->
                            if (epoch.get() == attempt && auth.currentUser?.uid == uid) auth.signOut()
                        }
                    }
                } finally {
                    signingIn.set(false)
                }
            }
        }
    }

    override suspend fun token(uid: String, forceRefresh: Boolean): FirebaseBearerToken? = withContext(Dispatchers.IO) {
        val auth = authentication ?: return@withContext null
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: return@withContext null
        val result = try {
            val cached = user.getIdToken(forceRefresh).awaitValue()
            if (!forceRefresh && cached.expirationTimestamp * 1000 <= System.currentTimeMillis() + 30_000) {
                user.getIdToken(true).awaitValue()
            } else {
                cached
            }
        } catch (_: FirebaseNetworkException) {
            throw IOException("Firebase token refresh unavailable")
        } catch (invalid: FirebaseAuthInvalidUserException) {
            when (invalid.errorCode) {
                "ERROR_USER_NOT_FOUND" -> throw FirebaseIdentityMissing(uid)
                "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN" -> throw FirebaseReauthenticationRequired(uid)
                else -> return@withContext null
            }
        } catch (_: FirebaseAuthException) {
            return@withContext null
        }
        val value = result.token ?: return@withContext null
        if (auth.currentUser?.uid != uid) return@withContext null
        FirebaseBearerToken(uid, value, result.expirationTimestamp * 1000)
    }

    override suspend fun clear(uid: String) = withContext(Dispatchers.IO) {
        val auth = authentication ?: return@withContext
        if (auth.currentUser?.uid?.let { it != uid } == true) return@withContext
        epoch.incrementAndGet()
        auth.signOut()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: ClearCredentialException) {
            android.util.Log.w("StudioAuth", "Credential provider session cleanup unavailable")
        }
    }

    private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { completed ->
            if (continuation.isActive) {
                if (completed.isCanceled) {
                    continuation.resumeWithException(CancellationException())
                } else if (completed.isSuccessful) {
                    continuation.resume(completed.result)
                } else {
                    continuation.resumeWithException(completed.exception ?: IOException("Authentication unavailable"))
                }
            }
        }
    }

    private companion object {
        const val APP_NAME = "studio-auth"
    }
}
