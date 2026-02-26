package com.zkbackup.app.service

import android.os.FileObserver
import android.util.Log
import com.zkbackup.app.data.db.entity.BackupFileEntity
import com.zkbackup.app.data.repository.BackupRepository
import com.zkbackup.app.util.HashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches user-configured directories for new/modified files using [FileObserver].
 *
 * Each watched directory gets its own recursive FileObserver. When a file is
 * created or modified, we hash it and register it in Room for the upload pipeline.
 */
@Singleton
class FileWatcherManager @Inject constructor(
    private val repository: BackupRepository
) {
    private val TAG = "FileWatcherManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val observers = mutableListOf<FileObserver>()

    /** Start watching the given directories. Call [stop] before calling this again. */
    fun start(directories: List<File>) {
        stop()
        for (dir in directories) {
            if (!dir.exists() || !dir.isDirectory) {
                Log.w(TAG, "Skipping non-existent directory: ${dir.absolutePath}")
                continue
            }
            val observer = createObserver(dir)
            observer.startWatching()
            observers.add(observer)
            Log.i(TAG, "Watching: ${dir.absolutePath}")
        }
    }

    /** Stop all observers. */
    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }

    private fun createObserver(dir: File): FileObserver {
        // CLOSE_WRITE fires after a file is fully written — safest trigger.
        // MOVED_TO catches files moved into the watched directory.
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO

        return object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val file = File(dir, path)
                if (!file.isFile) return
                if (file.isHidden || file.name.startsWith(".")) return
                if (file.length() == 0L) return

                Log.d(TAG, "File event ($event): ${file.absolutePath}")
                scope.launch { onFileDetected(file) }
            }
        }
    }

    private suspend fun onFileDetected(file: File) {
        try {
            // Skip if already tracked
            val existing = repository.getFileByPath(file.absolutePath)
            if (existing != null) return

            val hash = HashUtil.sha256(file)
            val id = repository.registerFile(
                path = file.absolutePath,
                hash = hash,
                size = file.length()
            )
            if (id > 0) {
                Log.i(TAG, "Registered: ${file.name} (hash=${hash.take(12)}…, id=$id)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ${file.absolutePath}", e)
        }
    }
}
