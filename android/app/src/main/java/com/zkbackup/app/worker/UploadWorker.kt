package com.zkbackup.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zkbackup.app.upload.UploadManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager one-shot worker that processes the upload queue.
 *
 * Enqueued by [PeriodicScanWorker] or the foreground service when new files
 * are discovered and network is available. WorkManager handles constraints
 * (network, battery) and retries automatically.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadManager: UploadManager
) : CoroutineWorker(context, params) {

    private val TAG = "UploadWorker"

    override suspend fun doWork(): Result {
        Log.i(TAG, "Upload worker started")
        return try {
            val uploaded = uploadManager.processQueue()
            Log.i(TAG, "Upload worker finished — $uploaded files processed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Upload worker failed", e)
            Result.retry()
        }
    }
}
