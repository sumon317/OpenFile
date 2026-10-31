package com.example.core.cloud

import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.core.database.CloudAccountEntity
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

enum class CloudProviderKind(
    val id: String,
    val displayName: String,
    val providerType: ProviderType,
    val authEndpoint: String,
    val tokenEndpoint: String,
    val defaultScope: String,
    val redirectUri: String
) {
    GOOGLE_DRIVE(
        id = "GOOGLE_DRIVE",
        displayName = "Google Drive",
        providerType = ProviderType.GOOGLE_DRIVE,
        authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        tokenEndpoint = "https://oauth2.googleapis.com/token",
        defaultScope = "https://www.googleapis.com/auth/drive",
        redirectUri = "com.openfile.manager:/oauth2redirect"
    ),
    ONEDRIVE(
        id = "ONEDRIVE",
        displayName = "Microsoft OneDrive",
        providerType = ProviderType.ONEDRIVE,
        authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
        defaultScope = "Files.ReadWrite.All offline_access User.Read",
        redirectUri = "com.openfile.manager://oauth2redirect"
    ),
    DROPBOX(
        id = "DROPBOX",
        displayName = "Dropbox",
        providerType = ProviderType.DROPBOX,
        authEndpoint = "https://www.dropbox.com/oauth2/authorize",
        tokenEndpoint = "https://api.dropboxapi.com/oauth2/token",
        defaultScope = "files.metadata.read files.content.read files.content.write",
        redirectUri = "com.openfile.manager://oauth2redirect"
    );

    fun defaultClientIdFromBuildConfig(): String {
        val raw = when (this) {
            GOOGLE_DRIVE -> runCatching { BuildConfig.GOOGLE_DRIVE_CLIENT_ID }.getOrDefault("")
            ONEDRIVE -> runCatching { BuildConfig.ONEDRIVE_CLIENT_ID }.getOrDefault("")
            DROPBOX -> runCatching { BuildConfig.DROPBOX_APP_KEY }.getOrDefault("")
        }
        return if (raw.startsWith("YOUR_")) "" else raw
    }
}

data class PkceSession(
    val provider: CloudProviderKind,
    val clientId: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val authorizationUrl: String
)

data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long
)

/**
 * Modular, official REST + OAuth 2.0 PKCE engine for Google Drive v3, Microsoft Graph OneDrive,
 * and Dropbox API v2.
 */
object CloudStorageEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Generates an RFC 7636 OAuth 2.0 PKCE authorization session and browser URL.
     */
    fun createPkceAuthorizationSession(
        provider: CloudProviderKind,
        customClientId: String
    ): PkceSession {
        val effectiveClientId = customClientId.trim().ifEmpty { provider.defaultClientIdFromBuildConfig() }
        require(effectiveClientId.isNotBlank()) {
            "Please enter a valid OAuth 2.0 Client ID / App Key for ${provider.displayName} (or set it in the AI Studio Secrets panel)."
        }
        val verifierBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val codeVerifier = Base64.encodeToString(
            verifierBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val challengeBytes = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = Base64.encodeToString(
            challengeBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val state = Base64.encodeToString(
            ByteArray(12).also { SecureRandom().nextBytes(it) },
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val uriBuilder = Uri.parse(provider.authEndpoint).buildUpon()
            .appendQueryParameter("client_id", effectiveClientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", provider.redirectUri)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)

        if (provider == CloudProviderKind.DROPBOX) {
            uriBuilder.appendQueryParameter("token_access_type", "offline")
        } else {
            uriBuilder.appendQueryParameter("scope", provider.defaultScope)
            uriBuilder.appendQueryParameter("access_type", "offline")
        }

        return PkceSession(
            provider = provider,
            clientId = effectiveClientId,
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge,
            state = state,
            authorizationUrl = uriBuilder.build().toString()
        )
    }

    /**
     * Exchanges an OAuth 2.0 authorization code for an access token and refresh token using PKCE.
     */
    suspend fun exchangeAuthorizationCode(
        session: PkceSession,
        authorizationCode: String
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder()
                .add("client_id", session.clientId)
                .add("grant_type", "authorization_code")
                .add("code", authorizationCode.trim())
                .add("redirect_uri", session.provider.redirectUri)
                .add("code_verifier", session.codeVerifier)
                .build()

            val request = Request.Builder()
                .url(session.provider.tokenEndpoint)
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("OAuth token exchange failed (HTTP ${response.code}): $bodyStr")
                }
                val json = JSONObject(bodyStr)
                OAuthTokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresInSeconds = json.optLong("expires_in", 3600L)
                )
            }
        }
    }

    /**
     * Refreshes an expired OAuth 2.0 access token using the stored [refreshToken].
     */
    suspend fun refreshAccessToken(
        provider: CloudProviderKind,
        clientId: String,
        refreshToken: String
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(provider.tokenEndpoint)
                .post(formBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Token refresh failed (HTTP ${response.code}): $bodyStr")
                }
                val json = JSONObject(bodyStr)
                OAuthTokenResponse(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
                    expiresInSeconds = json.optLong("expires_in", 3600L)
                )
            }
        }
    }

    /**
     * Lists files in a cloud directory for Google Drive v3, Microsoft OneDrive, or Dropbox v2.
     */
    suspend fun listCloudDirectory(
        account: CloudAccountEntity,
        accessToken: String,
        folderIdOrPath: String
    ): Result<List<FileReference>> = withContext(Dispatchers.IO) {
        runCatching {
            when (CloudProviderKind.valueOf(account.provider)) {
                CloudProviderKind.GOOGLE_DRIVE -> listGoogleDriveFiles(accessToken, folderIdOrPath.ifBlank { "root" })
                CloudProviderKind.ONEDRIVE -> listOneDriveFiles(accessToken, folderIdOrPath.ifBlank { "root" })
                CloudProviderKind.DROPBOX -> listDropboxFiles(accessToken, folderIdOrPath)
            }
        }
    }

    suspend fun createCloudFolder(
        account: CloudAccountEntity,
        accessToken: String,
        parentIdOrPath: String,
        folderName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (CloudProviderKind.valueOf(account.provider)) {
                CloudProviderKind.GOOGLE_DRIVE -> {
                    val payload = JSONObject().apply {
                        put("name", folderName)
                        put("mimeType", "application/vnd.google-apps.folder")
                        put("parents", JSONArray().put(parentIdOrPath.ifBlank { "root" }))
                    }
                    val req = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Google Drive create folder failed (HTTP ${resp.code})")
                    }
                }
                CloudProviderKind.ONEDRIVE -> {
                    val endpoint = if (parentIdOrPath.isBlank() || parentIdOrPath == "root") {
                        "https://graph.microsoft.com/v1.0/me/drive/root/children"
                    } else {
                        "https://graph.microsoft.com/v1.0/me/drive/items/$parentIdOrPath/children"
                    }
                    val payload = JSONObject().apply {
                        put("name", folderName)
                        put("folder", JSONObject())
                        put("@microsoft.graph.conflictBehavior", "rename")
                    }
                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("OneDrive create folder failed (HTTP ${resp.code})")
                    }
                }
                CloudProviderKind.DROPBOX -> {
                    val path = if (parentIdOrPath.isBlank() || parentIdOrPath == "/") "/$folderName" else "$parentIdOrPath/$folderName"
                    val payload = JSONObject().apply {
                        put("path", path)
                        put("autorename", true)
                    }
                    val req = Request.Builder()
                        .url("https://api.dropboxapi.com/2/files/create_folder_v2")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Dropbox create folder failed (HTTP ${resp.code})")
                    }
                }
            }
        }
    }

    suspend fun deleteCloudItem(
        account: CloudAccountEntity,
        accessToken: String,
        target: FileReference
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (CloudProviderKind.valueOf(account.provider)) {
                CloudProviderKind.GOOGLE_DRIVE -> {
                    val req = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/${target.uriOrPath}")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .delete()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Google Drive delete failed (HTTP ${resp.code})")
                    }
                }
                CloudProviderKind.ONEDRIVE -> {
                    val req = Request.Builder()
                        .url("https://graph.microsoft.com/v1.0/me/drive/items/${target.uriOrPath}")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .delete()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("OneDrive delete failed (HTTP ${resp.code})")
                    }
                }
                CloudProviderKind.DROPBOX -> {
                    val payload = JSONObject().apply { put("path", target.uriOrPath) }
                    val req = Request.Builder()
                        .url("https://api.dropboxapi.com/2/files/delete_v2")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IllegalStateException("Dropbox delete failed (HTTP ${resp.code})")
                    }
                }
            }
        }
    }

    suspend fun downloadCloudFile(
        account: CloudAccountEntity,
        accessToken: String,
        target: FileReference,
        localDest: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val reqBuilder = Request.Builder().addHeader("Authorization", "Bearer $accessToken")
            val request = when (CloudProviderKind.valueOf(account.provider)) {
                CloudProviderKind.GOOGLE_DRIVE -> reqBuilder
                    .url("https://www.googleapis.com/drive/v3/files/${target.uriOrPath}?alt=media")
                    .get()
                    .build()
                CloudProviderKind.ONEDRIVE -> reqBuilder
                    .url("https://graph.microsoft.com/v1.0/me/drive/items/${target.uriOrPath}/content")
                    .get()
                    .build()
                CloudProviderKind.DROPBOX -> reqBuilder
                    .url("https://content.dropboxapi.com/2/files/download")
                    .addHeader("Dropbox-API-Arg", JSONObject().put("path", target.uriOrPath).toString())
                    .post("".toRequestBody(null))
                    .build()
            }
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Cloud download failed (HTTP ${resp.code})")
                val body = resp.body ?: throw IllegalStateException("Empty download stream")
                localDest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    localDest.outputStream().buffered().use { out -> input.copyTo(out) }
                }
            }
            localDest
        }
    }

    suspend fun uploadCloudFile(
        account: CloudAccountEntity,
        accessToken: String,
        parentIdOrPath: String,
        localSource: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = "application/octet-stream".toMediaType()
            val request = when (CloudProviderKind.valueOf(account.provider)) {
                CloudProviderKind.GOOGLE_DRIVE -> Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(localSource.asRequestBody(mediaType))
                    .build()
                CloudProviderKind.ONEDRIVE -> {
                    val parentSeg = if (parentIdOrPath.isBlank() || parentIdOrPath == "root") "root" else "items/$parentIdOrPath"
                    Request.Builder()
                        .url("https://graph.microsoft.com/v1.0/me/drive/$parentSeg:/${localSource.name}:/content")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .put(localSource.asRequestBody(mediaType))
                        .build()
                }
                CloudProviderKind.DROPBOX -> {
                    val destPath = if (parentIdOrPath.isBlank() || parentIdOrPath == "/") "/${localSource.name}" else "$parentIdOrPath/${localSource.name}"
                    val argJson = JSONObject().apply {
                        put("path", destPath)
                        put("mode", "overwrite")
                    }
                    Request.Builder()
                        .url("https://content.dropboxapi.com/2/files/upload")
                        .addHeader("Authorization", "Bearer $accessToken")
                        .addHeader("Dropbox-API-Arg", argJson.toString())
                        .post(localSource.asRequestBody(mediaType))
                        .build()
                }
            }
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Cloud upload failed (HTTP ${resp.code})")
            }
        }
    }

    private fun listGoogleDriveFiles(accessToken: String, folderId: String): List<FileReference> {
        val url = Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon()
            .appendQueryParameter("q", "'$folderId' in parents and trashed = false")
            .appendQueryParameter("fields", "files(id,name,mimeType,size,modifiedTime)")
            .appendQueryParameter("pageSize", "200")
            .build().toString()
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Google Drive API error (${resp.code}): $body")
            }
            val filesArr = JSONObject(body).optJSONArray("files") ?: JSONArray()
            val list = mutableListOf<FileReference>()
            for (i in 0 until filesArr.length()) {
                val item = filesArr.getJSONObject(i)
                val mime = item.optString("mimeType", "application/octet-stream")
                val isDir = mime == "application/vnd.google-apps.folder"
                list.add(
                    FileReference(
                        providerType = ProviderType.GOOGLE_DRIVE,
                        uriOrPath = item.getString("id"),
                        displayName = item.optString("name", "Untitled"),
                        parentUriOrPath = folderId,
                        isDirectory = isDir,
                        sizeBytes = item.optLong("size", 0L),
                        mimeType = mime
                    )
                )
            }
            return list
        }
    }

    private fun listOneDriveFiles(accessToken: String, folderId: String): List<FileReference> {
        val url = if (folderId == "root" || folderId == "/") {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children"
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("OneDrive Graph API error (${resp.code}): $body")
            }
            val arr = JSONObject(body).optJSONArray("value") ?: JSONArray()
            val list = mutableListOf<FileReference>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val isDir = item.has("folder")
                list.add(
                    FileReference(
                        providerType = ProviderType.ONEDRIVE,
                        uriOrPath = item.getString("id"),
                        displayName = item.optString("name", "Item"),
                        parentUriOrPath = folderId,
                        isDirectory = isDir,
                        sizeBytes = item.optLong("size", 0L)
                    )
                )
            }
            return list
        }
    }

    private fun listDropboxFiles(accessToken: String, folderPath: String): List<FileReference> {
        val cleanPath = if (folderPath == "/" || folderPath == "root") "" else folderPath
        val payload = JSONObject().apply {
            put("path", cleanPath)
            put("recursive", false)
        }
        val req = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Dropbox API error (${resp.code}): $body")
            }
            val entries = JSONObject(body).optJSONArray("entries") ?: JSONArray()
            val list = mutableListOf<FileReference>()
            for (i in 0 until entries.length()) {
                val item = entries.getJSONObject(i)
                val isDir = item.optString(".tag") == "folder"
                val pathDisplay = item.optString("path_display", "/${item.optString("name")}")
                list.add(
                    FileReference(
                        providerType = ProviderType.DROPBOX,
                        uriOrPath = pathDisplay,
                        displayName = item.optString("name", "Item"),
                        parentUriOrPath = cleanPath.ifEmpty { "/" },
                        isDirectory = isDir,
                        sizeBytes = item.optLong("size", 0L)
                    )
                )
            }
            return list
        }
    }
}
