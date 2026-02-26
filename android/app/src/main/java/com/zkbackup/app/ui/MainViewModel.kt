package com.zkbackup.app.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkbackup.app.data.preferences.AppPreferences
import com.zkbackup.app.data.repository.BackupRepository
import com.zkbackup.app.service.BackupForegroundService
import com.zkbackup.app.ui.setup.SetupActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: BackupRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    val uploadedCount: StateFlow<Int> = repository.countUploaded()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCount: StateFlow<Int> = repository.countPending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isBackupRunning = MutableStateFlow(false)

    /** Navigate to setup if first launch. */
    fun checkSetup(context: Context) {
        viewModelScope.launch {
            val setupDone = prefs.isSetupDone.first()
            if (!setupDone) {
                context.startActivity(Intent(context, SetupActivity::class.java))
            } else {
                // Check if backup was enabled
                isBackupRunning.value = prefs.backupEnabled.first()
            }
        }
    }

    fun toggleBackup(context: Context) {
        viewModelScope.launch {
            val running = isBackupRunning.value
            if (running) {
                // Stop
                val intent = Intent(context, BackupForegroundService::class.java).apply {
                    action = BackupForegroundService.ACTION_STOP
                }
                context.startService(intent)
                prefs.setBackupEnabled(false)
                isBackupRunning.value = false
            } else {
                // Start
                val intent = Intent(context, BackupForegroundService::class.java)
                context.startForegroundService(intent)
                prefs.setBackupEnabled(true)
                isBackupRunning.value = true
            }
        }
    }
}
