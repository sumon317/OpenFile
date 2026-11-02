package com.example.core.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.model.ApkArchiveInfo
import com.example.core.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages installed application discovery, APK metadata inspection, APK sharing,
 * and native Android PackageInstaller invocation.
 * Implements a resilient two-tier discovery strategy: combines `getInstalledPackages`
 * with `<queries>` launcher intent resolution so it works even under strict package visibility.
 */
class AppManagerEngine(
    private val context: Context,
    private val localProvider: LocalFileSystemProvider
) {

    suspend fun loadInstalledApps(includeSystemApps: Boolean): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val discoveredPackages = LinkedHashMap<String, PackageInfo>()

        // Tier 1: Direct package query
        runCatching {
            val allPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            for (pkg in allPackages) {
                discoveredPackages[pkg.packageName] = pkg
            }
        }

        // Tier 2: Fallback via <queries> ACTION_MAIN + CATEGORY_LAUNCHER
        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
            for (ri in resolveInfos) {
                val pkgName = ri.activityInfo?.packageName ?: continue
                if (pkgName !in discoveredPackages) {
                    runCatching {
                        val pkgInfo = pm.getPackageInfo(pkgName, 0)
                        discoveredPackages[pkgName] = pkgInfo
                    }
                }
            }
        }

        discoveredPackages.values.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystemApps && isSystem) return@mapNotNull null

            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(pkg.packageName)
            val sourceDir = appInfo.sourceDir ?: ""
            val apkFile = if (sourceDir.isNotEmpty()) File(sourceDir) else null
            val apkSize = apkFile?.length() ?: 0L
            val canShare = apkFile != null && apkFile.canRead()
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 24

            InstalledAppInfo(
                appName = label,
                packageName = pkg.packageName,
                versionName = pkg.versionName ?: "1.0",
                versionCode = vCode,
                apkPath = sourceDir,
                apkSizeBytes = apkSize,
                firstInstallTimeMs = pkg.firstInstallTime,
                lastUpdateTimeMs = pkg.lastUpdateTime,
                isSystemApp = isSystem,
                targetSdk = appInfo.targetSdkVersion,
                minSdk = minSdk,
                canShareApk = canShare
            )
        }.sortedBy { it.appName.lowercase() }
    }

    /**
     * Inspects an uninstalled `.apk` file on local storage using `getPackageArchiveInfo`.
     */
    suspend fun inspectApkFile(apkFile: File): Result<ApkArchiveInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            } ?: throw IllegalArgumentException("Not a valid Android APK package: ${apkFile.name}")

            val appInfo = pkgInfo.applicationInfo
            appInfo?.sourceDir = apkFile.absolutePath
            appInfo?.publicSourceDir = apkFile.absolutePath

            val appLabel = appInfo?.let {
                runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()
            } ?: pkgInfo.packageName

            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion ?: 24
            } else 24

            ApkArchiveInfo(
                appName = appLabel,
                packageName = pkgInfo.packageName,
                versionName = pkgInfo.versionName ?: "1.0",
                versionCode = vCode,
                minSdk = minSdk,
                targetSdk = appInfo?.targetSdkVersion ?: 34,
                requestedPermissions = pkgInfo.requestedPermissions?.toList() ?: emptyList(),
                fileReference = localProvider.toFileReference(apkFile)
            )
        }
    }

    fun launchApp(packageName: String): Result<Unit> = runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("Application '$packageName' does not expose a launchable activity.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    fun openSystemAppInfo(packageName: String): Result<Unit> = runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun shareApkOrFile(file: File, mimeType: String = "application/vnd.android.package-archive"): Result<Unit> = runCatching {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Triggers Android's native PackageInstaller for [apkFile]. Never attempts silent installation.
     */
    fun installApkWithSystemInstaller(apkFile: File): Result<Unit> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return@runCatching
        }
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
