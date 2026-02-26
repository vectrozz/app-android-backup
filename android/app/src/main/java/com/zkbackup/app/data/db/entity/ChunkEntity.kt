package com.zkbackup.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks individual chunk upload progress for a [BackupFileEntity].
 *
 * The client splits each encrypted file into 8 MB chunks and uploads them
 * independently. On failure, only the failed chunk is re-uploaded.
 */
@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = BackupFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id", "chunk_index"], unique = true)
    ]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** FK → backup_files.id */
    @ColumnInfo(name = "file_id")
    val fileId: Long,

    /** Zero-based chunk index within the file. */
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,

    /** SHA-256 hex digest of the encrypted chunk bytes. */
    @ColumnInfo(name = "chunk_hash")
    val chunkHash: String,

    /** Chunk size in bytes (encrypted). */
    @ColumnInfo(name = "chunk_size")
    val chunkSize: Long,

    /** PENDING | UPLOADING | UPLOADED | FAILED */
    @ColumnInfo(name = "status")
    val status: String = Status.PENDING
) {
    object Status {
        const val PENDING   = "PENDING"
        const val UPLOADING = "UPLOADING"
        const val UPLOADED  = "UPLOADED"
        const val FAILED    = "FAILED"
    }
}
