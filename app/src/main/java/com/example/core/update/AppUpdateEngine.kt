package com.example.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val isPrerelease: Boolean = false
)

data class AppUpdateUiState(
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val isChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestRelease: GitHubReleaseInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgressFraction: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedApkFile: File? = null,
    val waitingForInstallPermission: Boolean = false,
    val lastCheckedEpochMs: Long = 0L,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Self-contained GitHub Releases Auto-Updater for OpenFile.
 * Queries https://api.github.com/repos/sumon317/OpenFile/releases, compares semantic versions,
 * downloads the release APK asset with live byte progress, and delegates installation to Android's
 * native PackageInstaller via FileProvider without requiring third-party apps like Obtainium.
 */
class AppUpdateEngine(private val context: Context) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getCurrentVersionInfo(): Pair<String, Int> {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val vName = pkgInfo.versionName ?: BuildConfig.VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode
            }
            vName to vCode
        }.getOrDefault(BuildConfig.VERSION_NAME to BuildConfig.VERSION_CODE)
    }

    /**
     * Checks `https://api.github.com/repos/sumon317/OpenFile/releases/latest` (with fallback to `/releases`)
     * for the newest release that includes an `.apk` asset.
     */
    suspend fun fetchLatestRelease(): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val latestReq = Request.Builder()
                .url(LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenFile-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(latestReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    parseReleaseJson(bodyStr)?.let { return@runCatching it }
                }
            }

            // Fallback to /releases list in case the latest release is marked pre-release or has no APK in /latest
            val listReq = Request.Builder()
                .url(ALL_RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OpenFile-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("GitHub Releases API returned HTTP ${resp.code}")
                }
                val bodyStr = resp.body?.string().orEmpty()
                parseReleasesArrayJson(bodyStr)
                    ?: throw IllegalStateException("No published APK releases found at $RELEASES_PAGE_URL")
            }
        }
    }

    /**
     * Downloads the `.apk` asset from GitHub Releases into the app's FileProvider-exposed updates folder.
     */
    suspend fun downloadReleaseApk(
        release: GitHubReleaseInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(
                context.getExternalFilesDir("updates") ?: File(context.cacheDir, "updates"),
                release.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            ).apply { mkdirs() }

            val safeApkName = release.apkAssetName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

            val targetApkFile = File(updatesDir, safeApkName)
            if (targetApkFile.exists() && release.apkSizeBytes > 0L && targetApkFile.length() == release.apkSizeBytes) {
                onProgress(release.apkSizeBytes, release.apkSizeBytes)
                return@runCatching targetApkFile
            }

            val partFile = File(updatesDir, "$safeApkName.part")
            if (partFile.exists()) partFile.delete()

            val request = Request.Builder()
                .url(release.apkDownloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "OpenFile-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to download APK (HTTP ${response.code})")
                }
                val body = response.body ?: throw IllegalStateException("Empty APK response body")
                val expectedTotal = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastReportedMs = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReportedMs >= 80L || downloaded == expectedTotal) {
                                lastReportedMs = now
                                onProgress(downloaded, expectedTotal)
                            }
                        }
                        output.fd.sync()
                    }
                }
            }

            if (partFile.length() <= 0L) {
                partFile.delete()
                throw IllegalStateException("Downloaded APK file is empty.")
            }
            if (targetApkFile.exists()) targetApkFile.delete()
            if (!partFile.renameTo(targetApkFile)) {
                partFile.copyTo(targetApkFile, overwrite = true)
                partFile.delete()
            }
            targetApkFile
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings(): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun launchPackageInstaller(apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists() && apkFile.length() > 0L) {
            "Downloaded APK not found at ${apkFile.absolutePath}"
        }
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun openReleasesPageInBrowser(): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Cleans up all downloaded APK update files and temporary partial downloads
     * across external and cache update folders so the app's cache and storage
     * stay minimal after an update is installed.
     */
    fun cleanDownloadedUpdateApks(): Result<Int> = runCatching {
        var deletedFiles = 0
        val candidateRoots = listOfNotNull(
            context.getExternalFilesDir("updates"),
            File(context.cacheDir, "updates")
        )
        for (root in candidateRoots) {
            if (root.exists()) {
                val files = root.walkBottomUp().toList()
                for (file in files) {
                    if (file != root) {
                        if (file.isFile) deletedFiles++
                        file.delete()
                    }
                }
            }
        }
        deletedFiles
    }

    companion object {
        const val GITHUB_OWNER = "sumon317"
        const val GITHUB_REPO = "OpenFile"
        const val RELEASES_PAGE_URL = "https://github.com/sumon317/OpenFile/releases"
        const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val ALL_RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=10"

        fun normalizeVersionString(rawTag: String): String {
            return rawTag.trim()
                .removePrefix("v")
                .removePrefix("V")
                .removePrefix("release-")
                .trim()
        }

        /**
         * Compares semantic version tags (e.g. `v1.5.0` vs `1.4.0`).
         * Returns true if [remoteTag] is strictly newer than [currentVersion].
         */
        fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val remoteClean = normalizeVersionString(remoteTag)
            val currentClean = normalizeVersionString(currentVersion)
            if (remoteClean.isEmpty() || remoteClean.equals(currentClean, ignoreCase = true)) {
                return false
            }

            val remoteCore = remoteClean.substringBefore('-')
            val currentCore = currentClean.substringBefore('-')

            val remoteParts = remoteCore.split('.').map { it.toIntOrNull() ?: 0 }
            val currentParts = currentCore.split('.').map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(remoteParts.size, currentParts.size)

            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r > c
            }

            // If numeric parts are equal, a stable release (no '-') is newer than a pre-release ('-alpha', '-rc1')
            val remoteHasSuffix = remoteClean.contains('-')
            val currentHasSuffix = currentClean.contains('-')
            if (!remoteHasSuffix && currentHasSuffix) return true
            if (remoteHasSuffix && !currentHasSuffix) return false

            return remoteClean > currentClean
        }

        private val moshi: Moshi = Moshi.Builder().build()
        private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        private val listType = Types.newParameterizedType(List::class.java, mapType)

        fun parseReleaseJson(jsonStr: String): GitHubReleaseInfo? {
            if (jsonStr.isBlank()) return null
            return runCatching {
                val adapter = moshi.adapter<Map<String, Any?>>(mapType)
                val obj = adapter.fromJson(jsonStr) ?: return null
                parseReleaseMap(obj)
            }.getOrNull()
        }

        fun parseReleasesArrayJson(jsonArrayStr: String): GitHubReleaseInfo? {
            if (jsonArrayStr.isBlank()) return null
            return runCatching {
                val adapter = moshi.adapter<List<Map<String, Any?>>>(listType)
                val list = adapter.fromJson(jsonArrayStr) ?: return null
                for (obj in list) {
                    val parsed = parseReleaseMap(obj)
                    if (parsed != null) return@runCatching parsed
                }
                null
            }.getOrNull()
        }

        @Suppress("UNCHECKED_CAST")
        private fun parseReleaseMap(obj: Map<String, Any?>): GitHubReleaseInfo? {
            if (obj["draft"] as? Boolean == true) return null
            val tagName = (obj["tag_name"] as? String)?.trim().orEmpty()
            if (tagName.isEmpty()) return null

            val assets = obj["assets"] as? List<Map<String, Any?>> ?: return null
            var chosenName = ""
            var chosenUrl = ""
            var chosenSize = 0L

            for (asset in assets) {
                val name = (asset["name"] as? String).orEmpty()
                val downloadUrl = (asset["browser_download_url"] as? String).orEmpty()

                // =========================================================================
                // CRITICAL RELEASE SECURITY POLICY:
                // NEVER SEND, PUBLISH, OR ACCEPT DEBUG APKS FOR RELEASES!
                // Debug APKs are unoptimized, unminified, contain debug symbols, and are
                // signed with insecure development keys. Releases must strictly use signed
                // production release APKs.
                // =========================================================================
                if (name.contains("debug", ignoreCase = true)) {
                    continue
                }

                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    val isPreferredReleaseApk = name.contains("release", ignoreCase = true) ||
                        chosenUrl.isEmpty()
                    if (isPreferredReleaseApk) {
                        chosenName = name
                        chosenUrl = downloadUrl
                        chosenSize = (asset["size"] as? Number)?.toLong() ?: 0L
                        if (name.contains("release", ignoreCase = true)) break
                    }
                }
            }

            if (chosenUrl.isEmpty()) return null

            val title = (obj["name"] as? String).orEmpty().ifBlank { "OpenFile $tagName" }
            val body = (obj["body"] as? String).orEmpty().ifBlank { "New release $tagName available on GitHub." }
            val publishedAt = (obj["published_at"] as? String).orEmpty()
            val htmlUrl = (obj["html_url"] as? String).orEmpty().ifBlank { RELEASES_PAGE_URL }
            val isPrerelease = obj["prerelease"] as? Boolean ?: false

            return GitHubReleaseInfo(
                tagName = tagName,
                versionName = normalizeVersionString(tagName),
                releaseTitle = title,
                changelog = body,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                apkAssetName = chosenName,
                apkDownloadUrl = chosenUrl,
                apkSizeBytes = chosenSize,
                isPrerelease = isPrerelease
            )
        }
    }
}
