package com.example.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@SuppressLint("DiscouragedApi")
internal fun getDefaultWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId == 0) return null
    return runCatching { context.getString(resId) }.getOrNull()?.takeIf { it.isNotBlank() }
}

internal fun getAppSigningSha1(context: Context): String? = runCatching {
    val pm = context.packageManager
    val pkgName = context.packageName
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
        info.signingInfo?.apkContentsSigners
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES).signatures
    }
    val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching null
    val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
    digest.joinToString(":") { "%02X".format(it) }
}.getOrNull()

private fun sha1Hint(context: Context): String {
    val sha1 = getAppSigningSha1(context)
    return if (sha1 != null) {
        "If this happens right after selecting a Google account, verify APK SHA-1 ($sha1) for ${context.packageName} is added in Firebase Console → Project Settings."
    } else {
        "Verify your APK signing SHA-1 for ${context.packageName} is added in Firebase Console → Project Settings."
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val existing = Firebase.auth.currentUser
    if (existing != null) {
        onAuthSuccess(existing)
        return
    }
    val clientId = getDefaultWebClientId(context) ?: run {
        onUnauthenticated()
        return
    }
    val targetContext: Context = context.findActivity() ?: context
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
            val cred = credentialManager.getCredential(targetContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                val user = authResult.user ?: Firebase.auth.currentUser
                if (user != null) {
                    onAuthSuccess(user)
                } else {
                    onUnauthenticated()
                }
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("Auth", "Silent auto sign-in skipped: ${e.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (String) -> Unit = {}
) {
    val clientId = getDefaultWebClientId(context) ?: run {
        onAuthError("Google Sign-In configuration missing: google-services.json / default_web_client_id not found.")
        return
    }
    val activity = context.findActivity() ?: run {
        onAuthError("Google Sign-In requires an active screen (Activity context not found).")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                if (token.isBlank()) {
                    onAuthError("Google returned an empty ID token.")
                    return@launch
                }
                val authResult = Firebase.auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null)
                ).await()
                val user = authResult.user ?: Firebase.auth.currentUser
                if (user != null) {
                    onAuthSuccess(user)
                } else {
                    onAuthError("Firebase authentication completed without an active user session.")
                }
            } else {
                onAuthError("Unexpected credential type received from Google Sign-In.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or rejected by Play Services: ${e.message}", e)
            onAuthCancelled(
                "Sign-in was cancelled or rejected by Google Play Services. ${sha1Hint(context)}"
            )
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google credentials available on device: ${e.message}", e)
            onAuthError("No Google account found on this device. Add a Google account in device Settings and retry.")
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Credential Manager error [${e.type}]: ${e.errorMessage}", e)
            onAuthError("Google Sign-In error (${e.type}): ${e.errorMessage ?: e.localizedMessage}. ${sha1Hint(context)}")
        } catch (e: FirebaseAuthException) {
            Log.e("Auth", "FirebaseAuth error [${e.errorCode}]: ${e.message}", e)
            onAuthError("Firebase Authentication failed [${e.errorCode}]: ${e.localizedMessage ?: "Unknown auth error"}")
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
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
