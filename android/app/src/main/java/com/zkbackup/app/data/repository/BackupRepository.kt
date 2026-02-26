package com.zkbackup.app.data.repository

import com.zkbackup.app.data.db.dao.BackupFileDao
import com.zkbackup.app.data.db.dao.ChunkDao
import com.zkbackup.app.data.db.entity.BackupFileEntity
import com.zkbackup.app.data.db.entity.ChunkEntity
import com.zkbackup.app.util.Constants
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for backup file state.
 * Wraps Room DAOs and adds business logic (e.g. "should this file be uploaded?").
 */
@Singleton
class BackupRepository @Inject constructor(
    private val fileDao: BackupFileDao,
    private val chunkDao: ChunkDao
) {
    // ── File operations ───────────────────────────────────────

    /**
     * Register a newly discovered file. Returns the entity ID, or -1 if
     * the hash is already known (dedup).
     */
    suspend fun registerFile(path: String, hash: String, size: Long): Long {
        val existing = fileDao.getByHash(hash)
        if (existing != null) return -1L   // already tracked — skip

        val entity = BackupFileEntity(
            filePath = path,
            fileHash = hash,
            fileSize = size
        )
        return fileDao.insert(entity)
    }

    /** Get files that are ready for the upload pipeline. */
    suspend fun getPendingUploads(): List<BackupFileEntity> =
        fileDao.getPendingUploads(maxRetries = Constants.MAX_CHUNK_RETRIES)

    suspend fun getFileById(id: Long): BackupFileEntity? = fileDao.getById(id)
    suspend fun getFileByHash(hash: String): BackupFileEntity? = fileDao.getByHash(hash)
    suspend fun getFileByPath(path: String): BackupFileEntity? = fileDao.getByPath(path)

    suspend fun updateFile(file: BackupFileEntity) = fileDao.update(file)
    suspend fun updateFileStatus(id: Long, status: String) = fileDao.updateStatus(id, status)

    suspend fun markUploading(id: Long, uploadId: String) =
        fileDao.updateStatusAndUploadId(id, BackupFileEntity.Status.UPLOADING, uploadId)

    suspend fun markUploaded(id: Long) = fileDao.markUploaded(id)
    suspend fun markFailed(id: Long, error: String?) = fileDao.markFailed(id, error)

    /** Reset any files stuck in UPLOADING (e.g. after a crash). */
    suspend fun resetStuckUploads() = fileDao.resetStuckUploads()

    // ── Chunk operations ──────────────────────────────────────

    suspend fun insertChunks(chunks: List<ChunkEntity>) = chunkDao.insertAll(chunks)
    suspend fun getChunksForFile(fileId: Long) = chunkDao.getChunksForFile(fileId)
    suspend fun getPendingChunks(fileId: Long) = chunkDao.getPendingChunks(fileId)
    suspend fun updateChunkStatus(chunkId: Long, status: String) = chunkDao.updateStatus(chunkId, status)
    suspend fun countUploadedChunks(fileId: Long) = chunkDao.countUploadedChunks(fileId)

    // ── Observable counts for UI ──────────────────────────────

    fun countUploaded(): Flow<Int> = fileDao.countUploaded()
    fun countPending(): Flow<Int> = fileDao.countPending()
    fun countFailed(): Flow<Int> = fileDao.countFailed()
}
