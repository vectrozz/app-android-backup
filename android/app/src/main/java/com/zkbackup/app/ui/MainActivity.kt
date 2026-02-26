package com.zkbackup.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zkbackup.app.R
import com.zkbackup.app.databinding.ActivityMainBinding
import com.zkbackup.app.service.BackupForegroundService
import com.zkbackup.app.ui.setup.SetupActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // ── Permission launchers ──────────────────────────────────

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless — service works without notification on older OS */ }

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkPermissionsAndProceed() }

    // ── Lifecycle ─────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeState()
        checkPermissionsAndProceed()
    }

    private fun setupUI() {
        binding.btnToggleBackup.setOnClickListener {
            viewModel.toggleBackup(this)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uploadedCount.collect { count ->
                        binding.tvFileCount.text = getString(R.string.main_files_backed_up, count)
                    }
                }
                launch {
                    viewModel.pendingCount.collect { count ->
                        binding.tvPending.text = getString(R.string.main_pending, count)
                        binding.tvPending.isVisible = count > 0
                    }
                }
                launch {
                    viewModel.isBackupRunning.collect { running ->
                        binding.btnToggleBackup.text = getString(
                            if (running) R.string.main_stop_backup else R.string.main_start_backup
                        )
                        binding.tvStatus.text = getString(
                            if (running) R.string.main_status_uploading else R.string.main_status_idle
                        )
                    }
                }
            }
        }
    }

    // ── Permissions ───────────────────────────────────────────

    private fun checkPermissionsAndProceed() {
        // 1) MANAGE_EXTERNAL_STORAGE (Android 11+)
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            storagePermission.launch(intent)
            return
        }

        // 2) POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 3) Check if setup is done
        viewModel.checkSetup(this)
    }
}
