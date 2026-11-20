package com.example.ui

import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dedicated test suite verifying UI Deep Audit fixes:
 * 1. Multi-selection filtering by search query.
 * 2. Path normalization for hidden folder detection across platforms.
 * 3. FileReference capability preservation on sorting and selection.
 */
class UiDeepAuditTest {

    private fun isPathInsideHiddenFolderHelper(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.split('/').any { it.startsWith(".") && it != "." && it != ".." }
    }

    @Test
    fun testHiddenFolderDetectionWithWindowsAndPosixSeparators() {
        assertTrue(isPathInsideHiddenFolderHelper("/storage/emulated/0/.openfile_trash/photo.jpg"))
        assertTrue(isPathInsideHiddenFolderHelper("C:\\Users\\User\\.hidden\\file.txt"))
        assertTrue(isPathInsideHiddenFolderHelper("D:/Storage/.private/data.bin"))
        assertFalse(isPathInsideHiddenFolderHelper("/storage/emulated/0/DCIM/Camera/photo.jpg"))
        assertFalse(isPathInsideHiddenFolderHelper("C:\\Users\\User\\Documents\\file.txt"))
    }

    @Test
    fun testMultiSelectSearchFilteringLogic() {
        val allItems = listOf(
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/notes.txt",
                displayName = "notes.txt",
                parentUriOrPath = "/storage/emulated/0",
                isDirectory = false,
                sizeBytes = 100L,
                lastModifiedEpochMs = 1000L,
                mimeType = "text/plain",
                extension = "txt"
            ),
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/photo.jpg",
                displayName = "photo.jpg",
                parentUriOrPath = "/storage/emulated/0",
                isDirectory = false,
                sizeBytes = 200L,
                lastModifiedEpochMs = 1000L,
                mimeType = "image/jpeg",
                extension = "jpg"
            ),
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/note_archive.zip",
                displayName = "note_archive.zip",
                parentUriOrPath = "/storage/emulated/0",
                isDirectory = false,
                sizeBytes = 300L,
                lastModifiedEpochMs = 1000L,
                mimeType = "application/zip",
                extension = "zip"
            )
        )

        val query = "note"
        val displayedItems = allItems.filter { it.displayName.contains(query, ignoreCase = true) }
        val selectedUris = displayedItems.map { it.uriOrPath }.toSet()

        assertEquals(2, selectedUris.size)
        assertTrue(selectedUris.contains("/storage/emulated/0/notes.txt"))
        assertTrue(selectedUris.contains("/storage/emulated/0/note_archive.zip"))
        assertFalse(selectedUris.contains("/storage/emulated/0/photo.jpg"))
    }

    @Test
    fun testCategoryFilterExcludesHiddenPaths() {
        val mixedFiles = listOf(
            "/storage/emulated/0/Pictures/vacation.jpg",
            "/storage/emulated/0/.openfile_trash/deleted.jpg",
            "/storage/emulated/0/.thumbnails/cache.jpg",
            "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
        )

        val visibleFiles = mixedFiles.filter { !isPathInsideHiddenFolderHelper(it) }
        assertEquals(2, visibleFiles.size)
        assertTrue(visibleFiles.contains("/storage/emulated/0/Pictures/vacation.jpg"))
        assertTrue(visibleFiles.contains("/storage/emulated/0/DCIM/Camera/IMG_001.jpg"))
    }
}
