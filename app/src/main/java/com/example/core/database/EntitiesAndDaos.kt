package com.example.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["uriOrPath"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val providerType: String,
    val uriOrPath: String,
    val isDirectory: Boolean,
    val mimeType: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uriOrPath: String,
    val providerType: String,
    val displayName: String,
    val parentUriOrPath: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long
)

@Entity(tableName = "network_profiles")
data class NetworkProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val protocol: String, // FTP, FTPS, SFTP, SMB
    val host: String,
    val port: Int,
    val username: String,
    val secretAlias: String?, // Reference into KeystoreCredentialVault
    val remoteInitialPath: String = "/",
    val shareName: String = "", // For SMB shares
    val expectedHostKeyFingerprint: String? = null, // SHA-256 SSH host key fingerprint for SFTP
    val isAnonymous: Boolean = false,
    val usePassiveMode: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalProviderType: String,
    val originalUriOrPath: String,
    val originalParentUriOrPath: String,
    val trashLocalFilePath: String,
    val displayName: String,
    val isDirectory: Boolean,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Checksum: String? = null,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transfer_jobs")
data class TransferJobEntity(
    @PrimaryKey val jobId: String,
    val operationType: String, // COPY, MOVE, EXTRACT, COMPRESS, UPLOAD, DOWNLOAD
    val title: String,
    val sourceProvider: String,
    val sourceUriOrPath: String,
    val destProvider: String,
    val destUriOrPath: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val totalFiles: Int,
    val processedFiles: Int,
    val status: String, // QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED, INTERRUPTED
    val verifyChecksum: Boolean = false,
    val checksumResult: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saf_grants")
data class SafGrantEntity(
    @PrimaryKey val treeUriString: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isPermissionValid: Boolean = true
)

@Dao
interface OpenFileDao {
    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE uriOrPath = :uriOrPath")
    suspend fun deleteBookmarkByPath(uriOrPath: String)

    // Recent Files
    @Query("SELECT * FROM recent_files ORDER BY lastAccessedAt DESC LIMIT 100")
    fun observeRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentFile(recent: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uriOrPath = :uriOrPath")
    suspend fun removeRecentFile(uriOrPath: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecentFiles()

    // Network Profiles
    @Query("SELECT * FROM network_profiles ORDER BY displayName ASC")
    fun observeNetworkProfiles(): Flow<List<NetworkProfileEntity>>

    @Query("SELECT * FROM network_profiles WHERE id = :id LIMIT 1")
    suspend fun getNetworkProfileById(id: Long): NetworkProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNetworkProfile(profile: NetworkProfileEntity): Long

    @Query("DELETE FROM network_profiles WHERE id = :id")
    suspend fun deleteNetworkProfile(id: Long)

    // Trash Items
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun observeTrashItems(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun getTrashItemById(id: Long): TrashItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashItemEntity): Long

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteTrashItemById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearAllTrashItems()

    // Transfer Jobs (Queue + Crash Recovery)
    @Query("SELECT * FROM transfer_jobs ORDER BY createdAt DESC LIMIT 50")
    fun observeTransferJobs(): Flow<List<TransferJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransferJob(job: TransferJobEntity)

    @Query("UPDATE transfer_jobs SET status = 'INTERRUPTED', errorMessage = 'Interrupted by process exit' WHERE status IN ('RUNNING', 'QUEUED')")
    suspend fun markRunningJobsAsInterrupted()

    @Query("DELETE FROM transfer_jobs WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED', 'INTERRUPTED')")
    suspend fun clearFinishedTransferJobs()

    @Query("DELETE FROM transfer_jobs WHERE jobId = :jobId")
    suspend fun deleteTransferJob(jobId: String)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun observeSearchHistory(): Flow<List<SearchQueryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchQueryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // SAF Persisted Grants
    @Query("SELECT * FROM saf_grants ORDER BY addedAt DESC")
    fun observeSafGrants(): Flow<List<SafGrantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSafGrant(grant: SafGrantEntity)

    @Query("DELETE FROM saf_grants WHERE treeUriString = :treeUriString")
    suspend fun deleteSafGrant(treeUriString: String)
}
