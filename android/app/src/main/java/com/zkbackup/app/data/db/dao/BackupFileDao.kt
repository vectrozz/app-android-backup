package com.zkbackup.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zkbackup.app.data.db.entity.BackupFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupFileDao {

    // ── Inserts ───────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(file: BackupFileEntity): Long

    @Update
    suspend fun update(file: BackupFileEntity)

    // ── Queries by status ─────────────────────────────────────
    @Query("SELECT * FROM backup_files WHERE status = :status ORDER BY discovered_at ASC")
    suspend fun getByStatus(status: String): List<BackupFileEntity>

    @Query("SELECT * FROM backup_files WHERE status IN (:statuses) ORDER BY discovered_at ASC")
    suspend fun getByStatuses(statuses: List<String>): List<BackupFileEntity>

    /** Files that need uploading (discovered, failed with retries left). */
    @Query("""
        SELECT * FROM backup_files 
        WHERE status IN ('DISCOVERED', 'FAILED') 
          AND retry_count < :maxRetries
        ORDER BY discovered_at ASC
        LIMIT :limit
    """)
    suspend fun getPendingUploads(maxRetries: Int, limit: Int = 50): List<BackupFileEntity>

    // ── Queries for UI ────────────────────────────────────────
    @Query("SELECT COUNT(*) FROM backup_files WHERE status = 'UPLOADED'")
    fun countUploaded(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_files WHERE status IN ('DISCOVERED','HASHING','ENCRYPTING','UPLOADING')")
    fun countPending(): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_files WHERE status = 'FAILED'")
    fun countFailed(): Flow<Int>

    // ── Lookup ────────────────────────────────────────────────
    @Query("SELECT * FROM backup_files WHERE file_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): BackupFileEntity?

    @Query("SELECT * FROM backup_files WHERE file_path = :path LIMIT 1")
    suspend fun getByPath(path: String): BackupFileEntity?

    @Query("SELECT * FROM backup_files WHERE id = :id")
    suspend fun getById(id: Long): BackupFileEntity?

    // ── Status updates ────────────────────────────────────────
    @Query("UPDATE backup_files SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE backup_files SET status = :status, upload_id = :uploadId WHERE id = :id")
    suspend fun updateStatusAndUploadId(id: Long, status: String, uploadId: String)

    @Query("UPDATE backup_files SET status = 'UPLOADED', uploaded_at = :timestamp WHERE id = :id")
    suspend fun markUploaded(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE backup_files SET status = 'FAILED', retry_count = retry_count + 1, last_error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)

    @Query("UPDATE backup_files SET status = 'FAILED', retry_count = 0 WHERE status = 'UPLOADING'")
    suspend fun resetStuckUploads()
}
