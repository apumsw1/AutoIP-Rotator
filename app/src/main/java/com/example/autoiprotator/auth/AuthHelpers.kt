package com.example.autoiprotator.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.autoiprotator.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val activityOrContext: Context = context.findActivity() ?: context
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val response = withTimeoutOrNull(2500L) {
                credentialManager.getCredential(activityOrContext, request)
            }
            val cred = response?.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (immediateDismissHint: String?) -> Unit = {}
) {
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Unable to start Google Sign-In: host Activity not found.")
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val primaryRequest = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val cred = credentialManager.getCredential(activity, primaryRequest).credential
            signInWithGoogleCredential(cred, onAuthSuccess, onAuthError)
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val hint = if (elapsedMs < 350L) {
                "Google Sign-In closed immediately. Make sure a Google account is signed in on this device/emulator and SHA-1 matches your Firebase app."
            } else {
                null
            }
            onAuthCancelled(hint)
        } catch (e: NoCredentialException) {
            Log.w("Auth", "GetSignInWithGoogleOption returned NoCredentialException; trying authorized/all Google ID picker", e)
            tryFallbackGoogleIdSignIn(
                activity = activity,
                credentialManager = credentialManager,
                clientId = clientId,
                onAuthSuccess = onAuthSuccess,
                onAuthError = onAuthError,
                onAuthCancelled = onAuthCancelled
            )
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Google Sign-In failed (${e.type}): ${e.errorMessage}", e)
            onAuthError(formatCredentialError(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }
}

private suspend fun tryFallbackGoogleIdSignIn(
    activity: Activity,
    credentialManager: CredentialManager,
    clientId: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: (immediateDismissHint: String?) -> Unit
) {
    val fallbackOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(false)
        .build()
    val fallbackRequest = GetCredentialRequest.Builder()
        .addCredentialOption(fallbackOption)
        .build()

    try {
        val cred = credentialManager.getCredential(activity, fallbackRequest).credential
        signInWithGoogleCredential(cred, onAuthSuccess, onAuthError)
    } catch (e: GetCredentialCancellationException) {
        Log.w("Auth", "Fallback Google ID Sign-In cancelled or dismissed: ${e.message}", e)
        onAuthCancelled(null)
    } catch (e: NoCredentialException) {
        Log.e("Auth", "No Google credentials available on device", e)
        onAuthError(
            "No Google account found or Play Services rejected app signature. " +
                "Sign in to a Google account in Device Settings (and ensure CI APK uses DEBUG_KEYSTORE_BASE64)."
        )
    } catch (e: GetCredentialException) {
        Log.e("Auth", "Fallback Google ID Sign-In failed (${e.type}): ${e.errorMessage}", e)
        onAuthError(formatCredentialError(e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Auth", "Fallback Google ID Sign-In failed", e)
        onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
    }
}

private suspend fun signInWithGoogleCredential(
    cred: androidx.credentials.Credential,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(
            GoogleAuthProvider.getCredential(token, null)
        ).await()
        onAuthSuccess()
    } else {
        onAuthError("Unexpected credential type received from Google.")
    }
}

private fun formatCredentialError(e: GetCredentialException): String {
    val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() }
        ?: e.localizedMessage?.takeIf { it.isNotBlank() }
        ?: e.type
    return when {
        detail.contains("28444", ignoreCase = true) ||
            detail.contains("developer", ignoreCase = true) ||
            detail.contains("10:", ignoreCase = true) ->
            "App signing certificate (SHA-1) does not match Firebase ($detail). Build with DEBUG_KEYSTORE_BASE64 or register this APK's SHA-1."
        else -> "Google Sign-In error: $detail"
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    beforeFirebaseSignOut: suspend () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                beforeFirebaseSignOut()
            } catch (e: Exception) {
                Log.w("Auth", "Cleanup before sign-out warning: ${e.message}")
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
