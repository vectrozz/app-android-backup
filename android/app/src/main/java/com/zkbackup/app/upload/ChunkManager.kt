package com.zkbackup.app.upload

import com.zkbackup.app.crypto.CryptoManager
import com.zkbackup.app.util.Constants
import com.zkbackup.app.util.HashUtil
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits an encrypted file into 8 MB chunks for upload.
 *
 * Flow:
 * 1. Read the plaintext file.
 * 2. Encrypt it in streaming mode → temp file.
 * 3. Split the encrypted temp file into chunks.
 *
 * Each chunk is a [ChunkData] with its bytes, index, and SHA-256 hash.
 */
@Singleton
class ChunkManager @Inject constructor(
    private val cryptoManager: CryptoManager
) {
    data class ChunkData(
        val index: Int,
        val bytes: ByteArray,
        val hash: String,
        val size: Long
    )

    data class EncryptedFileInfo(
        val encryptedSize: Long,
        val chunkCount: Int,
        val tempFile: File
    )

    /**
     * Encrypt [sourceFile] to a temp file and return metadata.
     * Caller must delete [EncryptedFileInfo.tempFile] when done.
     */
    fun encryptToTemp(sourceFile: File, encryptionKey: ByteArray, tempDir: File): EncryptedFileInfo {
        val tempFile = File(tempDir, "enc_${System.currentTimeMillis()}.tmp")
        tempFile.parentFile?.mkdirs()

        FileInputStream(sourceFile).use { input ->
            tempFile.outputStream().use { output ->
                cryptoManager.encryptStream(encryptionKey, input, output)
            }
        }

        val encryptedSize = tempFile.length()
        val chunkCount = ((encryptedSize + Constants.CHUNK_SIZE - 1) / Constants.CHUNK_SIZE).toInt()

        return EncryptedFileInfo(
            encryptedSize = encryptedSize,
            chunkCount = chunkCount,
            tempFile = tempFile
        )
    }

    /**
     * Read chunk [index] from an encrypted temp file.
     * Returns null if the index is out of range.
     */
    fun readChunk(encryptedFile: File, index: Int): ChunkData? {
        val offset = index.toLong() * Constants.CHUNK_SIZE
        if (offset >= encryptedFile.length()) return null

        val remaining = encryptedFile.length() - offset
        val chunkSize = minOf(remaining, Constants.CHUNK_SIZE).toInt()

        val bytes = ByteArray(chunkSize)
        FileInputStream(encryptedFile).use { fis ->
            fis.skip(offset)
            var totalRead = 0
            while (totalRead < chunkSize) {
                val read = fis.read(bytes, totalRead, chunkSize - totalRead)
                if (read == -1) break
                totalRead += read
            }
        }

        return ChunkData(
            index = index,
            bytes = bytes,
            hash = HashUtil.sha256(bytes),
            size = chunkSize.toLong()
        )
    }
}
