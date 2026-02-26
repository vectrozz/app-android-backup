package com.zkbackup.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zk_prefs")

/**
 * Thin wrapper around Jetpack DataStore for app settings.
 *
 * Sensitive secrets (JWT tokens, encryption salt) live here encrypted via
 * DataStore — NOT in plain SharedPreferences.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // ── Keys ──────────────────────────────────────────────────
    private object Keys {
        val SERVER_URL       = stringPreferencesKey("server_url")
        val ACCESS_TOKEN     = stringPreferencesKey("access_token")
        val REFRESH_TOKEN    = stringPreferencesKey("refresh_token")
        val DEVICE_ID        = stringPreferencesKey("device_id")
        val ENCRYPTION_SALT  = stringPreferencesKey("encryption_salt")   // hex-encoded
        val IS_SETUP_DONE    = booleanPreferencesKey("is_setup_done")
        val BACKUP_ENABLED   = booleanPreferencesKey("backup_enabled")
        val SELF_HOSTED      = booleanPreferencesKey("self_hosted")
        val WATCH_DIRS       = stringSetPreferencesKey("watch_dirs")
    }

    // ── Reads (Flow) ──────────────────────────────────────────
    val serverUrl: Flow<String>      = context.dataStore.data.map { it[Keys.SERVER_URL] ?: "" }
    val accessToken: Flow<String>    = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] ?: "" }
    val refreshToken: Flow<String>   = context.dataStore.data.map { it[Keys.REFRESH_TOKEN] ?: "" }
    val deviceId: Flow<String>       = context.dataStore.data.map { it[Keys.DEVICE_ID] ?: "" }
    val encryptionSalt: Flow<String> = context.dataStore.data.map { it[Keys.ENCRYPTION_SALT] ?: "" }
    val isSetupDone: Flow<Boolean>   = context.dataStore.data.map { it[Keys.IS_SETUP_DONE] ?: false }
    val backupEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BACKUP_ENABLED] ?: false }
    val selfHosted: Flow<Boolean>    = context.dataStore.data.map { it[Keys.SELF_HOSTED] ?: false }
    val watchDirs: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.WATCH_DIRS] ?: com.zkbackup.app.util.Constants.DEFAULT_WATCH_DIRS.toSet()
    }

    // ── Writes ────────────────────────────────────────────────
    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun setTokens(access: String, refresh: String) {
        context.dataStore.edit {
            it[Keys.ACCESS_TOKEN] = access
            it[Keys.REFRESH_TOKEN] = refresh
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
    }

    suspend fun setEncryptionSalt(saltHex: String) {
        context.dataStore.edit { it[Keys.ENCRYPTION_SALT] = saltHex }
    }

    suspend fun setSetupDone(done: Boolean) {
        context.dataStore.edit { it[Keys.IS_SETUP_DONE] = done }
    }

    suspend fun setBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_ENABLED] = enabled }
    }

    suspend fun setSelfHosted(selfHosted: Boolean) {
        context.dataStore.edit { it[Keys.SELF_HOSTED] = selfHosted }
    }

    suspend fun setWatchDirs(dirs: Set<String>) {
        context.dataStore.edit { it[Keys.WATCH_DIRS] = dirs }
    }

    // ── Convenience (suspend, not flow) ───────────────────────
    suspend fun getAccessTokenOnce(): String = accessToken.first()
    suspend fun getRefreshTokenOnce(): String = refreshToken.first()
    suspend fun getServerUrlOnce(): String = serverUrl.first()
    suspend fun getDeviceIdOnce(): String = deviceId.first()
    suspend fun getEncryptionSaltOnce(): String = encryptionSalt.first()
    suspend fun getSelfHostedOnce(): Boolean = selfHosted.first()

    /** Wipe everything (logout). */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
