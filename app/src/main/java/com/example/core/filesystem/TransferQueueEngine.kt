package com.example.core.filesystem

import android.content.Context
import android.os.StatFs
import com.example.core.database.OpenFileDao
import com.example.core.database.TransferJobEntity
import com.example.core.database.TrashItemEntity
import com.example.core.model.ConflictResolution
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

enum class ClipboardMode {
    COPY,
    CUT
}

data class ClipboardState(
    val items: List<FileReference> = emptyList(),
    val mode: ClipboardMode = ClipboardMode.COPY
) {
    val isNotEmpty: Boolean get() = items.isNotEmpty()
}

data class ConflictPromptRequest(
    val sourceFile: FileReference,
    val destinationDirectory: FileReference,
    val conflictingName: String,
    val responseDeferred: CompletableDeferred<Pair<ConflictResolution, Boolean>>
)

data class UndoActionRecord(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val performUndo: suspend () -> Result<String>
)

/**
 * Unified Transfer Queue, Conflict Resolver, Scoped Trash Manager, and Undo Engine.
 * Supports local, SAF, and cross-provider streaming with pause/resume, cancellation,
 * optional post-copy SHA-256 verification, and crash-recovery logging in Room.
 */
class TransferQueueEngine(
    private val context: Context,
    private val dao: OpenFileDao,
    private val localProvider: LocalFileSystemProvider,
    private val safProvider: SafDocumentProvider,
    private val appScope: CoroutineScope,
    private val credentialVault: com.example.core.security.KeystoreCredentialVault = com.example.core.security.KeystoreCredentialVault(context)
) {
    private val _clipboard = MutableStateFlow(ClipboardState())
    val clipboard: StateFlow<ClipboardState> = _clipboard.asStateFlow()

    private val _conflictPrompt = MutableStateFlow<ConflictPromptRequest?>(null)
    val conflictPrompt: StateFlow<ConflictPromptRequest?> = _conflictPrompt.asStateFlow()

    private val _latestUndoAction = MutableStateFlow<UndoActionRecord?>(null)
    val latestUndoAction: StateFlow<UndoActionRecord?> = _latestUndoAction.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val pausedJobIds = mutableSetOf<String>()

    private val trashRootDir: File by lazy {
        File(context.filesDir, ".openfile_trash").apply { if (!exists()) mkdirs() }
    }

    fun copyToClipboard(items: List<FileReference>, mode: ClipboardMode) {
        _clipboard.value = ClipboardState(items = items, mode = mode)
    }

    fun clearClipboard() {
        _clipboard.value = ClipboardState()
    }

    fun dismissUndoBanner() {
        _latestUndoAction.value = null
    }

    fun pauseJob(jobId: String) {
        synchronized(pausedJobIds) {
            pausedJobIds.add(jobId)
        }
        appScope.launch(Dispatchers.IO) {
            // Update Room status
            // Job checks pausedJobIds in transfer loop
        }
    }

    fun resumeJob(jobId: String) {
        synchronized(pausedJobIds) {
            pausedJobIds.remove(jobId)
        }
    }

    fun cancelJob(jobId: String) {
        synchronized(pausedJobIds) {
            pausedJobIds.remove(jobId)
        }
        activeJobs.remove(jobId)?.cancel()
    }

    private fun providerFor(type: ProviderType): VirtualFileSystemProvider = when (type) {
        ProviderType.SAF -> safProvider
        else -> localProvider
    }

    /**
     * Generates a non-conflicting name like `report (1).pdf` or `Folder (2)` inside [parent].
     */
    suspend fun generateAutoRenamedName(
        provider: VirtualFileSystemProvider,
        parent: FileReference,
        originalName: String,
        isDirectory: Boolean
    ): String {
        val baseName = if (isDirectory || !originalName.contains('.')) {
            originalName
        } else {
            originalName.substringBeforeLast('.')
        }
        val ext = if (isDirectory || !originalName.contains('.')) {
            ""
        } else {
            "." + originalName.substringAfterLast('.')
        }
        var counter = 1
        while (counter < 10_000) {
            val candidate = "$baseName ($counter)$ext"
            if (!provider.exists(parent, candidate)) {
                return candidate
            }
            counter++
        }
        return "${baseName}_${System.currentTimeMillis()}$ext"
    }

    /**
     * Enqueues a Copy or Move batch operation into the Unified Transfer Queue.
     */
    fun enqueuePasteFromClipboard(
        destinationDir: FileReference,
        verifyChecksum: Boolean,
        defaultConflictPolicy: ConflictResolution? = null,
        onComplete: (Result<String>) -> Unit = {}
    ): String {
        val clip = _clipboard.value
        if (clip.items.isEmpty()) {
            onComplete(Result.failure(IllegalStateException("Clipboard is empty.")))
            return ""
        }
        val isMove = clip.mode == ClipboardMode.CUT
        val jobId = UUID.randomUUID().toString()
        val totalBytes = clip.items.sumOf { it.sizeBytes.coerceAtLeast(1024L) }

        val job = appScope.launch(Dispatchers.IO) {
            var stickyResolution: ConflictResolution? = defaultConflictPolicy
            var transferredBytes = 0L
            var processedFiles = 0
            val createdDestinations = mutableListOf<FileReference>()

            val initialEntity = TransferJobEntity(
                jobId = jobId,
                operationType = if (isMove) "MOVE" else "COPY",
                title = "${if (isMove) "Moving" else "Copying"} ${clip.items.size} item(s) to ${destinationDir.displayName}",
                sourceProvider = clip.items.first().providerType.name,
                sourceUriOrPath = clip.items.first().uriOrPath,
                destProvider = destinationDir.providerType.name,
                destUriOrPath = destinationDir.uriOrPath,
                totalBytes = totalBytes,
                transferredBytes = 0L,
                totalFiles = clip.items.size,
                processedFiles = 0,
                status = "RUNNING",
                verifyChecksum = verifyChecksum
            )
            try {
                runCatching { dao.upsertTransferJob(initialEntity) }
                var lastDbUpdateMs = 0L
                for (sourceItem in clip.items) {
                    coroutineContext.ensureActive()
                    waitIfPaused(jobId, initialEntity, transferredBytes, processedFiles)

                    val destProvider = providerFor(destinationDir.providerType)
                    var targetName = sourceItem.displayName

                    // Check for collision
                    if (destProvider.exists(destinationDir, targetName)) {
                        val isSameSourceAndDest = sourceItem.providerType == destinationDir.providerType &&
                            sourceItem.parentUriOrPath == destinationDir.uriOrPath &&
                            sourceItem.displayName == targetName

                        if (isSameSourceAndDest && isMove) {
                            // Moving a file onto itself in the same directory is a safe no-op
                            processedFiles++
                            createdDestinations.add(sourceItem)
                            continue
                        }

                        val resolution = if (isSameSourceAndDest && !isMove) {
                            // Copying a file into the same directory must auto-rename so it never deletes its own source
                            ConflictResolution.KEEP_BOTH
                        } else {
                            stickyResolution ?: run {
                                val deferred = CompletableDeferred<Pair<ConflictResolution, Boolean>>()
                                _conflictPrompt.value = ConflictPromptRequest(
                                    sourceFile = sourceItem,
                                    destinationDirectory = destinationDir,
                                    conflictingName = targetName,
                                    responseDeferred = deferred
                                )
                                val (choice, applyToAll) = deferred.await()
                                _conflictPrompt.value = null
                                if (applyToAll) stickyResolution = choice
                                choice
                            }
                        }

                        when (resolution) {
                            ConflictResolution.SKIP -> {
                                processedFiles++
                                continue
                            }
                            ConflictResolution.KEEP_BOTH -> {
                                targetName = generateAutoRenamedName(
                                    provider = destProvider,
                                    parent = destinationDir,
                                    originalName = targetName,
                                    isDirectory = sourceItem.isDirectory
                                )
                            }
                            ConflictResolution.OVERWRITE -> {
                                if (destinationDir.providerType == ProviderType.LOCAL) {
                                    File(destinationDir.uriOrPath, targetName).deleteRecursively()
                                } else {
                                    destProvider.listChildren(destinationDir, showHidden = true)
                                        .getOrDefault(emptyList())
                                        .firstOrNull { it.displayName == targetName }
                                        ?.let { destProvider.delete(it) }
                                }
                            }
                        }
                    }

                    val transferredRef = transferItemRecursive(
                        jobId = jobId,
                        jobTemplate = initialEntity,
                        source = sourceItem,
                        destinationParent = destinationDir,
                        targetName = targetName,
                        isMove = isMove,
                        verifyChecksum = verifyChecksum,
                        onBytesDelta = { delta ->
                            transferredBytes += delta
                        }
                    )
                    createdDestinations.add(transferredRef)
                    processedFiles++
                    val now = System.currentTimeMillis()
                    if (now - lastDbUpdateMs >= 300L || processedFiles == clip.items.size) {
                        lastDbUpdateMs = now
                        dao.upsertTransferJob(
                            initialEntity.copy(
                                transferredBytes = transferredBytes.coerceAtMost(totalBytes),
                                processedFiles = processedFiles,
                                status = "RUNNING",
                                updatedAt = now
                            )
                        )
                    }
                }

                if (isMove) {
                    clearClipboard()
                } else if (createdDestinations.isNotEmpty()) {
                    _latestUndoAction.value = UndoActionRecord(
                        description = "Copied ${createdDestinations.size} item(s) to ${destinationDir.displayName}",
                        performUndo = {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val destProvider = providerFor(destinationDir.providerType)
                                    createdDestinations.forEach { destProvider.delete(it) }
                                    "Undid copy of ${createdDestinations.size} item(s)"
                                }
                            }
                        }
                    )
                }

                dao.upsertTransferJob(
                    initialEntity.copy(
                        transferredBytes = totalBytes,
                        processedFiles = clip.items.size,
                        status = "COMPLETED",
                        checksumResult = if (verifyChecksum) "SHA-256 Verified OK" else null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                onComplete(Result.success("${if (isMove) "Moved" else "Copied"} $processedFiles item(s) to ${destinationDir.displayName}"))
            } catch (ce: CancellationException) {
                dao.upsertTransferJob(
                    initialEntity.copy(
                        transferredBytes = transferredBytes,
                        processedFiles = processedFiles,
                        status = "CANCELLED",
                        errorMessage = "Cancelled by user",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                onComplete(Result.failure(ce))
            } catch (e: Exception) {
                dao.upsertTransferJob(
                    initialEntity.copy(
                        transferredBytes = transferredBytes,
                        processedFiles = processedFiles,
                        status = "FAILED",
                        errorMessage = e.message ?: "Transfer failed",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                onComplete(Result.failure(e))
            } finally {
                _conflictPrompt.value = null
                activeJobs.remove(jobId)
            }
        }
        activeJobs[jobId] = job
        return jobId
    }

    private suspend fun waitIfPaused(
        jobId: String,
        entity: TransferJobEntity,
        transferredBytes: Long,
        processedFiles: Int
    ) {
        var markedPaused = false
        while (synchronized(pausedJobIds) { jobId in pausedJobIds }) {
            coroutineContext.ensureActive()
            if (!markedPaused) {
                markedPaused = true
                dao.upsertTransferJob(
                    entity.copy(
                        transferredBytes = transferredBytes,
                        processedFiles = processedFiles,
                        status = "PAUSED",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            delay(250)
        }
        if (markedPaused) {
            dao.upsertTransferJob(
                entity.copy(
                    transferredBytes = transferredBytes,
                    processedFiles = processedFiles,
                    status = "RUNNING",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun transferItemRecursive(
        jobId: String,
        jobTemplate: TransferJobEntity,
        source: FileReference,
        destinationParent: FileReference,
        targetName: String,
        isMove: Boolean,
        verifyChecksum: Boolean,
        onBytesDelta: (Long) -> Unit
    ): FileReference {
        coroutineContext.ensureActive()
        val srcProvider = providerFor(source.providerType)
        val dstProvider = providerFor(destinationParent.providerType)

        // Prevent copying or moving a folder into itself or its own subdirectory
        if (source.isDirectory && source.providerType == destinationParent.providerType) {
            if (source.providerType == ProviderType.LOCAL) {
                val srcCanonical = runCatching { File(source.uriOrPath).canonicalPath }.getOrDefault(source.uriOrPath)
                val dstCanonical = runCatching { File(destinationParent.uriOrPath, targetName).canonicalPath }
                    .getOrDefault("${destinationParent.uriOrPath}/$targetName")
                if (dstCanonical == srcCanonical || dstCanonical.startsWith("$srcCanonical${File.separator}")) {
                    throw IllegalArgumentException("Cannot copy or move folder '${source.displayName}' into itself.")
                }
            } else {
                val srcNorm = source.uriOrPath.trimEnd('/')
                val dstNorm = "${destinationParent.uriOrPath.trimEnd('/')}/$targetName"
                if (dstNorm == srcNorm || dstNorm.startsWith("$srcNorm/")) {
                    throw IllegalArgumentException("Cannot copy or move folder '${source.displayName}' into itself.")
                }
            }
        }

        // Fast atomic move when both source and destination are on LocalFileSystemProvider
        if (isMove &&
            source.providerType == ProviderType.LOCAL &&
            destinationParent.providerType == ProviderType.LOCAL
        ) {
            val srcFile = File(source.uriOrPath)
            val dstFile = File(destinationParent.uriOrPath, targetName)
            if (srcFile.canonicalPath == dstFile.canonicalPath) {
                return localProvider.toFileReference(dstFile)
            }
            if (srcFile.renameTo(dstFile)) {
                onBytesDelta(source.sizeBytes.coerceAtLeast(1024L))
                return localProvider.toFileReference(dstFile)
            }
        }

        if (source.isDirectory) {
            val children = srcProvider.listChildren(source, showHidden = true).getOrDefault(emptyList())
            val createdDir = dstProvider.createDirectory(destinationParent, targetName).getOrThrow()
            for (child in children) {
                transferItemRecursive(
                    jobId = jobId,
                    jobTemplate = jobTemplate,
                    source = child,
                    destinationParent = createdDir,
                    targetName = child.displayName,
                    isMove = isMove,
                    verifyChecksum = verifyChecksum,
                    onBytesDelta = onBytesDelta
                )
            }
            if (isMove) {
                srcProvider.delete(source)
            }
            return createdDir
        } else {
            // Stream file bytes
            val sourceSha256 = if (verifyChecksum) {
                srcProvider.openInputStream(source).getOrThrow().use {
                    ChecksumAndDuplicateEngine.computeChecksums(it).sha256Hex
                }
            } else null

            var destRef: FileReference? = null
            var bytesCopiedForFile = 0L
            try {
                srcProvider.openInputStream(source).getOrThrow().use { input ->
                    val (createdRef, output) = dstProvider.openOutputStream(destinationParent, targetName, append = false).getOrThrow()
                    destRef = createdRef
                    output.use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var read = input.read(buffer)
                        while (read > 0) {
                            coroutineContext.ensureActive()
                            out.write(buffer, 0, read)
                            bytesCopiedForFile += read
                            onBytesDelta(read.toLong())
                            read = input.read(buffer)
                        }
                        out.flush()
                    }
                }

                val finalDest = destRef ?: throw IllegalStateException("Destination reference was not created.")
                if (verifyChecksum && sourceSha256 != null) {
                    val destSha256 = dstProvider.openInputStream(finalDest).getOrThrow().use {
                        ChecksumAndDuplicateEngine.computeChecksums(it).sha256Hex
                    }
                    if (!sourceSha256.equals(destSha256, ignoreCase = true)) {
                        dstProvider.delete(finalDest)
                        throw IllegalStateException("SHA-256 verification mismatch for ${source.displayName}")
                    }
                }

                if (isMove) {
                    // Only delete source after verified 100% completion
                    srcProvider.delete(source).getOrThrow()
                }

                return if (finalDest.providerType == ProviderType.LOCAL) {
                    localProvider.toFileReference(File(finalDest.uriOrPath))
                } else {
                    finalDest.copy(sizeBytes = bytesCopiedForFile)
                }
            } catch (t: Throwable) {
                // Clean up partial destination file so source data is never lost
                destRef?.let { runCatching { dstProvider.delete(it) } }
                throw t
            }
        }
    }

    /**
     * Soft-deletes items into `.openfile_trash` with free-space preflight check and atomic rollback,
     * or performs permanent deletion when requested or for remote providers.
     */
    suspend fun deleteItems(
        items: List<FileReference>,
        moveToTrash: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var trashedCount = 0
            var permanentCount = 0
            val trashIdsCreated = mutableListOf<Long>()

            for (item in items) {
                coroutineContext.ensureActive()
                val canTrash = moveToTrash && (item.providerType == ProviderType.LOCAL || item.providerType == ProviderType.SAF)
                if (canTrash) {
                    val trashFile = File(trashRootDir, "${UUID.randomUUID()}_${item.displayName}")
                    val originalParent = item.parentUriOrPath ?: localProvider.getPrimaryRootDirectory().absolutePath

                    // Check free space when copying across volumes/SAF (guard against 0-total virtual StatFs)
                    val stat = runCatching { StatFs(trashRootDir.absolutePath) }.getOrNull()
                    val availableBytes = if (stat != null && stat.totalBytes > 0L) stat.availableBytes else Long.MAX_VALUE
                    if (item.sizeBytes > availableBytes - 10L * 1024 * 1024) {
                        throw IllegalStateException("Insufficient storage space to move '${item.displayName}' to Trash. Choose permanent delete instead.")
                    }

                    val movedOk = if (item.providerType == ProviderType.LOCAL) {
                        val src = File(item.uriOrPath)
                        if (src.renameTo(trashFile)) {
                            true
                        } else {
                            src.copyRecursively(trashFile, overwrite = true) && src.deleteRecursively()
                        }
                    } else {
                        // SAF: Copy stream to local trash first, verify, then delete SAF document
                        safProvider.openInputStream(item).getOrThrow().use { input ->
                            trashFile.outputStream().use { out -> input.copyTo(out) }
                        }
                        if (trashFile.length() == item.sizeBytes) {
                            safProvider.delete(item).getOrDefault(false)
                        } else {
                            trashFile.delete()
                            false
                        }
                    }

                    if (!movedOk) {
                        trashFile.deleteRecursively()
                        throw IllegalStateException("Failed to move '${item.displayName}' to Trash.")
                    }

                    val trashId = dao.insertTrashItem(
                        TrashItemEntity(
                            originalProviderType = item.providerType.name,
                            originalUriOrPath = item.uriOrPath,
                            originalParentUriOrPath = originalParent,
                            trashLocalFilePath = trashFile.absolutePath,
                            displayName = item.displayName,
                            isDirectory = item.isDirectory,
                            mimeType = item.mimeType,
                            sizeBytes = item.sizeBytes
                        )
                    )
                    trashIdsCreated.add(trashId)
                    trashedCount++
                } else {
                    val provider = providerFor(item.providerType)
                    provider.delete(item).getOrThrow()
                    permanentCount++
                }
            }

            if (trashIdsCreated.isNotEmpty()) {
                _latestUndoAction.value = UndoActionRecord(
                    description = "Moved $trashedCount item(s) to Trash",
                    performUndo = {
                        restoreTrashItemsById(trashIdsCreated)
                    }
                )
            }

            when {
                trashedCount > 0 && permanentCount > 0 -> "Moved $trashedCount to Trash, permanently deleted $permanentCount"
                trashedCount > 0 -> "Moved $trashedCount item(s) to Trash"
                else -> "Permanently deleted $permanentCount item(s)"
            }
        }
    }

    /**
     * Restores items from `.openfile_trash` to their original directory (or fallback Downloads/Workspace if missing).
     */
    suspend fun restoreTrashItemsById(trashIds: List<Long>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            var restored = 0
            for (id in trashIds) {
                val entity = dao.getTrashItemById(id) ?: continue
                val trashFile = File(entity.trashLocalFilePath)
                if (!trashFile.exists()) {
                    dao.deleteTrashItemById(id)
                    continue
                }
                val targetParentDir = File(entity.originalParentUriOrPath).let {
                    if (it.exists() && it.canWrite()) it else localProvider.getPrimaryRootDirectory()
                }
                var restoreTarget = File(targetParentDir, entity.displayName)
                if (restoreTarget.exists()) {
                    val newName = generateAutoRenamedName(
                        provider = localProvider,
                        parent = localProvider.toFileReference(targetParentDir),
                        originalName = entity.displayName,
                        isDirectory = entity.isDirectory
                    )
                    restoreTarget = File(targetParentDir, newName)
                }
                val moved = if (trashFile.renameTo(restoreTarget)) {
                    true
                } else {
                    trashFile.copyRecursively(restoreTarget, overwrite = true) && trashFile.deleteRecursively()
                }
                if (!moved) {
                    throw IllegalStateException("Failed to restore '${entity.displayName}'.")
                }
                dao.deleteTrashItemById(id)
                restored++
            }
            "Restored $restored item(s) from Trash"
        }
    }

    suspend fun permanentlyDeleteTrashItem(entity: TrashItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(entity.trashLocalFilePath).deleteRecursively()
            dao.deleteTrashItemById(entity.id)
        }
    }

    suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            trashRootDir.listFiles()?.forEach { it.deleteRecursively() }
            dao.clearAllTrashItems()
        }
    }
}
