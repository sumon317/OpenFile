package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.database.BookmarkEntity
import com.example.core.database.TrashItemEntity
import com.example.core.model.FileReference
import com.example.core.model.InstalledAppInfo
import com.example.core.model.StorageAnalysisReport
import com.example.core.model.formatByteSize
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Visual Storage Analyzer with Canvas ring chart, category breakdown,
 * largest files, largest directories drill-down, extension distribution, and SHA-256 Duplicate Finder.
 */
@Composable
fun StorageAnalysisScreen(
    report: StorageAnalysisReport?,
    isScanning: Boolean,
    progressText: String,
    onRefreshScan: (File?) -> Unit,
    onCancelScan: () -> Unit,
    onOpenFileOrDirectory: (FileReference) -> Unit,
    onDeleteDuplicateFile: (FileReference) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Largest Files", "Largest Folders", "Duplicates")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_analysis_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Storage Analyzer", style = MaterialTheme.typography.titleLarge)
                if (report != null) {
                    Text(
                        text = "${report.scannedFilesCount} files across ${report.scannedDirectoriesCount} folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isScanning) {
                OutlinedButton(onClick = onCancelScan) {
                    Text("Cancel Scan")
                }
            } else {
                Button(
                    onClick = { onRefreshScan(null) },
                    modifier = Modifier.testTag("analyzer_rescan_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan")
                }
            }
        }

        // Sub-navigation tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    label = { Text(label) }
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))

        if (isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(progressText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }

        if (report == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { onRefreshScan(null) }) {
                    Text("Start Storage Analysis")
                }
            }
            return
        }

        when (selectedTab) {
            0 -> StorageOverviewTab(report = report)
            1 -> LargestFilesTab(
                files = report.largestFiles,
                onOpen = onOpenFileOrDirectory
            )
            2 -> LargestDirectoriesTab(
                directories = report.largestDirectories,
                onDrillDown = { dirRef ->
                    onRefreshScan(File(dirRef.uriOrPath))
                    selectedTab = 0
                },
                onOpenInBrowser = onOpenFileOrDirectory
            )
            3 -> DuplicateFilesTab(
                report = report,
                onDeleteDuplicate = onDeleteDuplicateFile
            )
        }
    }
}

@Composable
private fun StorageOverviewTab(report: StorageAnalysisReport) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Custom Canvas Multi-Segment Ring Chart
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(110.dp)) {
                            val strokeWidth = 18.dp.toPx()
                            drawArc(
                                color = Color.Gray.copy(alpha = 0.2f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                            var currentStartAngle = -90f
                            report.categoryBuckets.forEach { bucket ->
                                val sweep = (bucket.fractionOfTotal * 360f).coerceAtLeast(2f)
                                drawArc(
                                    color = Color(bucket.colorHex),
                                    startAngle = currentStartAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )
                                currentStartAngle += sweep
                            }
                        }
                        val pct = if (report.totalBytes > 0) {
                            ((report.usedBytes.toDouble() / report.totalBytes.toDouble()) * 100).toInt()
                        } else 0
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total Capacity: ${formatByteSize(report.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Used: ${formatByteSize(report.usedBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Available: ${formatByteSize(report.freeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApkGreen
                        )
                    }
                }
            }
        }

        item {
            Text("Usage by Category", style = MaterialTheme.typography.titleMedium)
        }

        items(report.categoryBuckets, key = { it.categoryName }) { bucket ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(bucket.colorHex))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${bucket.categoryName} (${bucket.fileCount} files)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(formatByteSize(bucket.bytesUsed), style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { bucket.fractionOfTotal },
                    color = Color(bucket.colorHex),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (report.extensionDistribution.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Top File Extensions", style = MaterialTheme.typography.titleMedium)
            }
            items(report.extensionDistribution, key = { it.first }) { (ext, count, bytes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$ext ($count files)",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Text(
                        text = formatByteSize(bytes),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LargestFilesTab(
    files: List<FileReference>,
    onOpen: (FileReference) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.uriOrPath }) { file ->
            Card(
                onClick = { onOpen(file) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(file.uriOrPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formatByteSize(file.sizeBytes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LargestDirectoriesTab(
    directories: List<Pair<FileReference, Long>>,
    onDrillDown: (FileReference) -> Unit,
    onOpenInBrowser: (FileReference) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(directories, key = { it.first.uriOrPath }) { (dirRef, totalSize) ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDrillDown(dirRef) }
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = FolderAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(dirRef.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(formatByteSize(totalSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    TextButton(onClick = { onOpenInBrowser(dirRef) }) {
                        Text("Browse")
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateFilesTab(
    report: StorageAnalysisReport,
    onDeleteDuplicate: (FileReference) -> Unit
) {
    if (report.duplicateGroups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No duplicate files detected (verified by SHA-256).")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(report.duplicateGroups, key = { it.sha256Hash }) { group ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${group.files.size} Identical Copies (${formatByteSize(group.fileSizeBytes)} each)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Reclaim ${formatByteSize(group.reclaimableBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "SHA-256: ${group.sha256Hash.take(24)}...",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    group.files.forEachIndexed { idx, fileRef ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (idx == 0) "[Original] " else ""}${fileRef.uriOrPath}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (idx > 0) {
                                IconButton(onClick = { onDeleteDuplicate(fileRef) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete duplicate",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Installed Application & APK Manager Screen.
 */
@Composable
fun ApplicationsScreen(
    apps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    isLoading: Boolean,
    onToggleSystemApps: (Boolean) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onShareApk: (File) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("applications_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed packages...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = includeSystemApps,
                onClick = { onToggleSystemApps(!includeSystemApps) },
                label = { Text("System Apps") }
            )
        }
        HorizontalDivider()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.Android,
                                        contentDescription = null,
                                        tint = ApkGreen,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = formatByteSize(app.apkSizeBytes),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Text(
                                text = "v${app.versionName} (${app.versionCode}) • SDK ${app.targetSdk} • Installed ${dateFormat.format(Date(app.firstInstallTimeMs))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onOpenAppInfo(app.packageName) }) {
                                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("App Info")
                                }
                                if (app.apkPath.isNotEmpty() && File(app.apkPath).canRead()) {
                                    TextButton(onClick = { onShareApk(File(app.apkPath)) }) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share APK")
                                    }
                                }
                                TextButton(onClick = { onLaunchApp(app.packageName) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bookmarks Manager Screen.
 */
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    onOpenBookmark: (BookmarkEntity) -> Unit,
    onRenameBookmark: (BookmarkEntity, String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit
) {
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bookmarks_screen")
    ) {
        Text(
            text = "Pinned Bookmarks (${bookmarks.size})",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No bookmarks yet. Use the file menu to pin frequent folders or files.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bookmarks, key = { it.id }) { bm ->
                    Card(
                        onClick = { onOpenBookmark(bm) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = FolderAmber)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(bm.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${bm.providerType} • ${bm.uriOrPath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editingBookmark = bm }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename bookmark")
                                }
                                IconButton(onClick = { onDeleteBookmark(bm) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove bookmark",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingBookmark != null) {
        val bm = editingBookmark!!
        var newTitle by remember(bm) { mutableStateOf(bm.title) }
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text("Rename Bookmark") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Bookmark title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameBookmark(bm, newTitle)
                        editingBookmark = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Scoped Trash Screen with Restore, Permanent Delete, and Empty Trash actions.
 */
@Composable
fun TrashScreen(
    trashItems: List<TrashItemEntity>,
    onRestoreItem: (TrashItemEntity) -> Unit,
    onDeletePermanently: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("trash_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Trash (${trashItems.size})", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Local & SAF soft-deleted items. Remote FTP/SFTP/SMB/Cloud deletions bypass local trash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trashItems.isNotEmpty()) {
                Button(onClick = onEmptyTrash) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Empty Trash")
                }
            }
        }
        HorizontalDivider()

        if (trashItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Trash is empty.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trashItems, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${formatByteSize(item.sizeBytes)} • From: ${item.originalParentUriOrPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                IconButton(onClick = { onRestoreItem(item) }) {
                                    Icon(
                                        Icons.Filled.RestoreFromTrash,
                                        contentDescription = "Restore ${item.displayName}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeletePermanently(item) }) {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        contentDescription = "Delete permanently",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
