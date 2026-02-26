package com.zkbackup.app.ui.setup

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkbackup.app.crypto.CryptoManager
import com.zkbackup.app.data.preferences.AppPreferences
import com.zkbackup.app.data.remote.ApiService
import com.zkbackup.app.upload.UploadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val apiService: ApiService,
    private val prefs: AppPreferences,
    private val cryptoManager: CryptoManager,
    private val uploadManager: UploadManager
) : ViewModel() {

    data class SetupUiState(
        val isAuthenticated: Boolean = false,
        val message: String? = null,
        val setupComplete: Boolean = false
    )

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    fun register(serverUrl: String, email: String, password: String, masterPassword: String, selfHosted: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = SetupUiState(message = "Registering…")

                prefs.setServerUrl(serverUrl)
                prefs.setSelfHosted(selfHosted)

                // 1. Register account
                val auth = apiService.register(email, password)
                prefs.setTokens(auth.access_token, auth.refresh_token)

                // 2. Register device
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val device = apiService.registerDevice(deviceName)
                prefs.setDeviceId(device.device_id)

                // 3. Setup encryption
                setupEncryption(masterPassword)

                _uiState.value = SetupUiState(
                    isAuthenticated = true,
                    message = "Account created! Tap Save to start backing up."
                )
            } catch (e: Exception) {
                _uiState.value = SetupUiState(message = "Registration failed: ${e.message}")
            }
        }
    }

    fun login(serverUrl: String, email: String, password: String, masterPassword: String, selfHosted: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = SetupUiState(message = "Logging in…")

                prefs.setServerUrl(serverUrl)
                prefs.setSelfHosted(selfHosted)

                // 1. Login
                val auth = apiService.login(email, password)
                prefs.setTokens(auth.access_token, auth.refresh_token)

                // 2. Register device (new device, existing account)
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                val device = apiService.registerDevice(deviceName)
                prefs.setDeviceId(device.device_id)

                // 3. Setup encryption
                setupEncryption(masterPassword)

                _uiState.value = SetupUiState(
                    isAuthenticated = true,
                    message = "Logged in! Tap Save to start backing up."
                )
            } catch (e: Exception) {
                _uiState.value = SetupUiState(message = "Login failed: ${e.message}")
            }
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            prefs.setSetupDone(true)
            prefs.setBackupEnabled(true)
            _uiState.value = _uiState.value.copy(setupComplete = true)
        }
    }

    private suspend fun setupEncryption(masterPassword: String) {
        // Generate and store salt (or reuse if already exists)
        var saltHex = prefs.getEncryptionSaltOnce()
        if (saltHex.isEmpty()) {
            val salt = cryptoManager.generateSalt()
            saltHex = salt.joinToString("") { "%02x".format(it) }
            prefs.setEncryptionSalt(saltHex)
        }

        // Derive and cache the encryption key in memory
        uploadManager.setMasterPassword(masterPassword, saltHex)
    }
}
