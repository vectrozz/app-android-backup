package com.zkbackup.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zkbackup.app.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Restarts [BackupForegroundService] after device reboot if backup was enabled.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val enabled = runBlocking { prefs.backupEnabled.first() }
        if (!enabled) return

        Log.i("BootReceiver", "Boot completed — restarting backup service")
        val serviceIntent = Intent(context, BackupForegroundService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
