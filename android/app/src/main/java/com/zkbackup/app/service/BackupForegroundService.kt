package com.zkbackup.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zkbackup.app.R
import com.zkbackup.app.ZkBackupApplication
import com.zkbackup.app.data.preferences.AppPreferences
import com.zkbackup.app.ui.MainActivity
import com.zkbackup.app.upload.UploadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that keeps the backup pipeline alive.
 *
 * Android 13/14 require a persistent notification for long-running work.
 * This service:
 * 1. Starts [FileWatcherManager] on the user's configured directories.
 * 2. Launches the [UploadManager] coroutine loop.
 * 3. Displays a persistent notification with current status.
 */
@AndroidEntryPoint
class BackupForegroundService : Service() {

    @Inject lateinit var fileWatcher: FileWatcherManager
    @Inject lateinit var uploadManager: UploadManager
    @Inject lateinit var prefs: AppPreferences

    private val TAG = "BackupService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification("Watching for new files…"))

        serviceScope.launch {
            // Resolve watch directories
            val dirs = resolveWatchDirs()
            fileWatcher.start(dirs)
            Log.i(TAG, "File watcher started on ${dirs.size} directories")

            // Start upload loop
            uploadManager.startUploadLoop()
        }

        return START_STICKY   // restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        fileWatcher.stop()
        uploadManager.stop()
        serviceScope.cancel()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, BackupForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ZkBackupApplication.CHANNEL_BACKUP)
            .setContentTitle(getString(R.string.notif_backup_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Helpers ───────────────────────────────────────────────

    private suspend fun resolveWatchDirs(): List<File> {
        val configuredDirs = prefs.watchDirs.first()
        val extRoot = Environment.getExternalStorageDirectory()
        return configuredDirs.map { File(extRoot, it) }.filter { it.exists() && it.isDirectory }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zkbackup.STOP"
    }
}
