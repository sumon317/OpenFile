package com.example.core.cloud

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device Google Drive authorization through Google Play services (Authorization API).
 *
 * No browser, redirect URI, client ID in code, or pasted code is needed: the user simply picks a
 * Google account on the device. Google matches the app by package name + signing SHA-1 against an
 * "Android" OAuth client registered once in Google Cloud Console.
 *
 * The API only returns short-lived access tokens (no refresh token); a fresh token is obtained
 * silently by calling [authorize] again once consent has been granted.
 */
object GoogleDriveOnDeviceAuth {

    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

    /** Marker stored in `CloudAccountEntity.clientId` for accounts connected through this path. */
    const val ON_DEVICE_CLIENT_MARKER = "ON_DEVICE_GOOGLE"

    private fun buildRequest(accountEmail: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
        if (!accountEmail.isNullOrBlank() && accountEmail.contains("@")) {
            builder.setAccount(Account(accountEmail, "com.google"))
        }
        return builder.build()
    }

    /**
     * Requests Drive access. If [AuthorizationResult.hasResolution] is true the caller must launch
     * [AuthorizationResult.getPendingIntent] to show Google's consent sheet.
     */
    suspend fun authorize(context: Context, accountEmail: String? = null): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context)
                .authorize(buildRequest(accountEmail))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    fun resultFromConsentIntent(context: Context, data: android.content.Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
}
