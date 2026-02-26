package com.zkbackup.app.worker

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zkbackup.app.data.preferences.AppPreferences
import com.zkbackup.app.data.repository.BackupRepository
import com.zkbackup.app.util.HashUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * WorkManager periodic fallback scanner.
 *
 * Runs every 15 minutes to catch files that [FileObserver] may have missed
 * (e.g. if the process was killed, or files appeared while observer was stopped).
 * Walks all configured watch directories and registers any unknown files.
 */
@HiltWorker
class PeriodicScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BackupRepository,
    private val prefs: AppPreferences
) : CoroutineWorker(context, params) {

    private val TAG = "PeriodicScanWorker"

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting periodic scan")

        val configuredDirs = prefs.watchDirs.first()
        val extRoot = Environment.getExternalStorageDirectory()
        var newFiles = 0

        for (dirName in configuredDirs) {
            val dir = File(extRoot, dirName)
            if (!dir.exists() || !dir.isDirectory) continue

            dir.walkTopDown()
                .filter { it.isFile && !it.isHidden && it.length() > 0 }
                .forEach { file ->
                    try {
                        // Skip if already tracked by path
                        if (repository.getFileByPath(file.absolutePath) != null) return@forEach

                        val hash = HashUtil.sha256(file)

                        // Skip if hash already known (same file, different path = dedup)
                        if (repository.getFileByHash(hash) != null) return@forEach

                        val id = repository.registerFile(
                            path = file.absolutePath,
                            hash = hash,
                            size = file.length()
                        )
                        if (id > 0) newFiles++
                    } catch (e: Exception) {
                        Log.w(TAG, "Error scanning ${file.absolutePath}", e)
                    }
                }
        }

        Log.i(TAG, "Scan complete — found $newFiles new files")
        return Result.success()
    }
}
