package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector

enum class DrawerSection(val title: String) {
    OVERVIEW("Overview"),
    CATEGORIES("Categories & Media"),
    REMOTE("Network & Remote"),
    TOOLS("Analysis & Tools")
}

/**
 * The 16 primary navigation destinations of OpenFile.
 */
enum class OpenFileDestination(
    val route: String,
    val title: String,
    val section: DrawerSection,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        route = "home",
        title = "Home",
        section = DrawerSection.OVERVIEW,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    INTERNAL_STORAGE(
        route = "internal_storage",
        title = "Internal Storage",
        section = DrawerSection.OVERVIEW,
        selectedIcon = Icons.Filled.PhoneAndroid,
        unselectedIcon = Icons.Outlined.PhoneAndroid,
        testTag = "nav_internal_storage"
    ),
    DOWNLOADS(
        route = "downloads",
        title = "Downloads",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
        testTag = "nav_downloads"
    ),
    IMAGES(
        route = "images",
        title = "Images",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.Filled.Image,
        unselectedIcon = Icons.Outlined.Image,
        testTag = "nav_images"
    ),
    VIDEOS(
        route = "videos",
        title = "Videos",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.Filled.VideoFile,
        unselectedIcon = Icons.Outlined.VideoFile,
        testTag = "nav_videos"
    ),
    AUDIO(
        route = "audio",
        title = "Audio",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.Filled.AudioFile,
        unselectedIcon = Icons.Outlined.AudioFile,
        testTag = "nav_audio"
    ),
    DOCUMENTS(
        route = "documents",
        title = "Documents",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.AutoMirrored.Filled.InsertDriveFile,
        unselectedIcon = Icons.AutoMirrored.Outlined.InsertDriveFile,
        testTag = "nav_documents"
    ),
    APPLICATIONS(
        route = "applications",
        title = "Applications",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.Filled.Android,
        unselectedIcon = Icons.Outlined.Android,
        testTag = "nav_applications"
    ),
    ARCHIVES(
        route = "archives",
        title = "Archives",
        section = DrawerSection.CATEGORIES,
        selectedIcon = Icons.Filled.FolderZip,
        unselectedIcon = Icons.Outlined.FolderZip,
        testTag = "nav_archives"
    ),
    RECENT_FILES(
        route = "recent_files",
        title = "Recent Files",
        section = DrawerSection.OVERVIEW,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_recent_files"
    ),
    LARGE_FILES(
        route = "large_files",
        title = "Large Files",
        section = DrawerSection.TOOLS,
        selectedIcon = Icons.Filled.Storage,
        unselectedIcon = Icons.Outlined.Storage,
        testTag = "nav_large_files"
    ),
    NETWORK(
        route = "network",
        title = "Network & FTP",
        section = DrawerSection.REMOTE,
        selectedIcon = Icons.Filled.Lan,
        unselectedIcon = Icons.Outlined.Lan,
        testTag = "nav_network"
    ),
    STORAGE_ANALYSIS(
        route = "storage_analysis",
        title = "Storage Analysis",
        section = DrawerSection.TOOLS,
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        testTag = "nav_storage_analysis"
    ),
    BOOKMARKS(
        route = "bookmarks",
        title = "Bookmarks",
        section = DrawerSection.OVERVIEW,
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        testTag = "nav_bookmarks"
    ),
    TRASH(
        route = "trash",
        title = "Trash",
        section = DrawerSection.TOOLS,
        selectedIcon = Icons.Filled.DeleteOutline,
        unselectedIcon = Icons.Outlined.DeleteOutline,
        testTag = "nav_trash"
    ),
    SETTINGS(
        route = "settings",
        title = "Settings",
        section = DrawerSection.TOOLS,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
}
