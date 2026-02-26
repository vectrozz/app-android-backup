package com.zkbackup.app.upload

import android.content.Context
import android.util.Log
import com.zkbackup.app.crypto.CryptoManager
import com.zkbackup.app.data.db.entity.BackupFileEntity
import com.zkbackup.app.data.db.entity.ChunkEntity
import com.zkbackup.app.data.preferences.AppPreferences
import com.zkbackup.app.data.remote.ApiService
import com.zkbackup.app.data.repository.BackupRepository
import com.zkbackup.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full upload pipeline for each pending file:
 *
 * 1. Encrypt → temp file.
 * 2. POST /upload/init → get upload_id (or skip if already_exists).
 * 3. For each chunk: PUT /upload/{id}/chunk/{n}.
 * 4. POST /upload/{id}/complete.
 * 5. Clean up temp file, mark as UPLOADED in Room.
 *
 * Runs as a long-lived coroutine loop inside [BackupForegroundService],
 * or as a one-shot via [processQueue] from [UploadWorker].
 */
@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val apiService: ApiService,
    private val chunkManager: ChunkManager,
    private val cryptoManager: CryptoManager,
    private val prefs: AppPreferences
) {
    private val TAG = "UploadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    /** Start a continuous upload loop (used by foreground service). */
    fun startUploadLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            Log.i(TAG, "Upload loop started")
            // Reset any files stuck in UPLOADING from a previous crash
            repository.resetStuckUploads()

            while (isActive) {
                val processed = processQueue()
                if (processed == 0) {
                    delay(10_000) // idle — wait 10s before checking again
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * Process pending uploads. Returns the number of files processed.
     * Called by both the foreground service loop and [UploadWorker].
     */
    suspend fun processQueue(): Int {
        val pending = repository.getPendingUploads()
        if (pending.isEmpty()) return 0

        var processed = 0
        for (file in pending) {
            try {
                uploadFile(file)
                processed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for ${file.filePath}", e)
                repository.markFailed(file.id, e.message)
            }
        }
        return processed
    }

    private suspend fun uploadFile(file: BackupFileEntity) {
        val sourceFile = File(file.filePath)
        if (!sourceFile.exists()) {
            repository.markFailed(file.id, "File no longer exists")
            return
        }

        // ── Step 1: Encrypt ───────────────────────────────────
        repository.updateFileStatus(file.id, BackupFileEntity.Status.ENCRYPTING)
        val encryptionKey = getEncryptionKey()
        val tempDir = File(context.cacheDir, "upload_temp")

        val encInfo = chunkManager.encryptToTemp(sourceFile, encryptionKey, tempDir)
        Log.d(TAG, "Encrypted ${file.filePath}: ${encInfo.encryptedSize} bytes, ${encInfo.chunkCount} chunks")

        try {
            // Update file metadata
            repository.updateFile(file.copy(
                encryptedSize = encInfo.encryptedSize,
                chunkCount = encInfo.chunkCount,
                status = BackupFileEntity.Status.UPLOADING
            ))

            // ── Step 2: Init upload ───────────────────────────
            val deviceId = prefs.getDeviceIdOnce()
            val initResp = apiService.uploadInit(
                fileHash = file.fileHash,
                encryptedSize = encInfo.encryptedSize,
                chunkCount = encInfo.chunkCount,
                deviceId = deviceId
            )

            if (initResp.already_exists) {
                Log.i(TAG, "File already on server: ${file.fileHash.take(12)}…")
                repository.markUploaded(file.id)
                return
            }

            repository.markUploading(file.id, initResp.upload_id)

            // ── Step 3: Upload chunks ─────────────────────────
            // Create chunk records in Room
            val chunkEntities = (0 until encInfo.chunkCount).map { idx ->
                val chunkData = chunkManager.readChunk(encInfo.tempFile, idx)
                    ?: throw IllegalStateException("Chunk $idx read failed")
                ChunkEntity(
                    fileId = file.id,
                    chunkIndex = idx,
                    chunkHash = chunkData.hash,
                    chunkSize = chunkData.size
                )
            }
            repository.insertChunks(chunkEntities)

            // Check which chunks the server already has (resume support)
            val status = apiService.uploadStatus(initResp.upload_id)
            val alreadyUploaded = status.chunks_received.toSet()

            for (idx in 0 until encInfo.chunkCount) {
                if (idx in alreadyUploaded) {
                    repository.updateChunkStatus(chunkEntities[idx].id, ChunkEntity.Status.UPLOADED)
                    continue
                }

                val chunkData = chunkManager.readChunk(encInfo.tempFile, idx)
                    ?: throw IllegalStateException("Chunk $idx read failed")

                retryWithBackoff {
                    repository.updateChunkStatus(chunkEntities[idx].id, ChunkEntity.Status.UPLOADING)
                    apiService.uploadChunk(initResp.upload_id, idx, chunkData.bytes)
                    repository.updateChunkStatus(chunkEntities[idx].id, ChunkEntity.Status.UPLOADED)
                }
            }

            // ── Step 4: Complete ──────────────────────────────
            apiService.uploadComplete(initResp.upload_id)
            repository.markUploaded(file.id)
            Log.i(TAG, "Upload complete: ${file.fileHash.take(12)}…")

        } finally {
            // Clean up temp file
            encInfo.tempFile.delete()
        }
    }

    /** Get the user's derived encryption key. */
    private suspend fun getEncryptionKey(): ByteArray {
        val saltHex = prefs.getEncryptionSaltOnce()
        require(saltHex.isNotEmpty()) { "Encryption salt not set — run setup first" }

        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        // The master password is not stored — for now we cache the derived key
        // in memory. In production, prompt the user or use Android Keystore.
        // TODO: proper key management — see Phase 3.
        return derivedKeyCache ?: throw IllegalStateException(
            "Encryption key not available. Call setMasterPassword() first."
        )
    }

    // ── In-memory key cache (cleared on process death) ────────
    private var derivedKeyCache: ByteArray? = null

    /**
     * Derive the encryption key from the master password and cache it in memory.
     * Called during setup/login — the password is NOT persisted.
     */
    fun setMasterPassword(password: String, saltHex: String) {
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        derivedKeyCache = cryptoManager.deriveKey(password, salt)
    }

    fun clearKey() {
        derivedKeyCache?.fill(0)
        derivedKeyCache = null
    }

    // ── Retry helper ──────────────────────────────────────────
    private suspend fun retryWithBackoff(
        maxRetries: Int = Constants.MAX_CHUNK_RETRIES,
        block: suspend () -> Unit
    ) {
        var attempt = 0
        var delayMs = Constants.RETRY_BACKOFF_MS
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxRetries) throw e
                Log.w(TAG, "Retry $attempt/$maxRetries after ${delayMs}ms", e)
                delay(delayMs)
                delayMs *= 2
            }
        }
    }
}
