package com.zkbackup.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zkbackup.app.data.db.dao.BackupFileDao
import com.zkbackup.app.data.db.dao.ChunkDao
import com.zkbackup.app.data.db.entity.BackupFileEntity
import com.zkbackup.app.data.db.entity.ChunkEntity

@Database(
    entities = [BackupFileEntity::class, ChunkEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun backupFileDao(): BackupFileDao
    abstract fun chunkDao(): ChunkDao
}
