package com.zkbackup.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zkbackup.app.data.db.entity.ChunkEntity

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM chunks WHERE file_id = :fileId ORDER BY chunk_index ASC")
    suspend fun getChunksForFile(fileId: Long): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE file_id = :fileId AND status IN ('PENDING', 'FAILED') ORDER BY chunk_index ASC")
    suspend fun getPendingChunks(fileId: Long): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks WHERE file_id = :fileId AND status = 'UPLOADED'")
    suspend fun countUploadedChunks(fileId: Long): Int

    @Query("UPDATE chunks SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM chunks WHERE file_id = :fileId")
    suspend fun deleteChunksForFile(fileId: Long)
}
