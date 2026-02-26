package com.zkbackup.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks every file the client has seen.
 *
 * - [fileHash] is the SHA-256 of the **original plaintext** file (used for dedup).
 * - [status] drives the upload pipeline state machine.
 * - Real file paths are stored locally only — the server never sees them.
 */
@Entity(
    tableName = "backup_files",
    indices = [
        Index(value = ["file_hash"], unique = true),
        Index(value = ["status"])
    ]
)
data class BackupFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Absolute path on device (local only, never sent to server). */
    @ColumnInfo(name = "file_path")
    val filePath: String,

    /** SHA-256 hex digest of the original (plaintext) file. */
    @ColumnInfo(name = "file_hash")
    val fileHash: String,

    /** Original file size in bytes (plaintext). */
    @ColumnInfo(name = "file_size")
    val fileSize: Long,

    /** Encrypted file size in bytes (after AES-GCM overhead). */
    @ColumnInfo(name = "encrypted_size")
    val encryptedSize: Long = 0,

    /** Number of 8 MB chunks. */
    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int = 0,

    /** Upload state: DISCOVERED → HASHING → ENCRYPTING → UPLOADING → UPLOADED | FAILED | SKIPPED. */
    @ColumnInfo(name = "status")
    val status: String = Status.DISCOVERED,

    /** Server-assigned upload ID (returned by POST /upload/init). Null until upload starts. */
    @ColumnInfo(name = "upload_id")
    val uploadId: String? = null,

    /** Retry counter (resets on success). */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    /** Last error message (null if none). */
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    /** Epoch millis when the file was first discovered. */
    @ColumnInfo(name = "discovered_at")
    val discoveredAt: Long = System.currentTimeMillis(),

    /** Epoch millis when upload completed (null if not yet). */
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long? = null
) {
    /** Pipeline states. */
    object Status {
        const val DISCOVERED  = "DISCOVERED"
        const val HASHING     = "HASHING"
        const val ENCRYPTING  = "ENCRYPTING"
        const val UPLOADING   = "UPLOADING"
        const val UPLOADED    = "UPLOADED"
        const val FAILED      = "FAILED"
        const val SKIPPED     = "SKIPPED"
    }
}
