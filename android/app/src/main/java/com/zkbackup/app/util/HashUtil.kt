package com.zkbackup.app.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * SHA-256 helpers. Used to fingerprint files before encryption so we can
 * de-duplicate on the client side (Room) and verify integrity on the server.
 */
object HashUtil {

    private const val BUFFER_SIZE = 8192

    /** Compute SHA-256 hex digest of a [file]. Streams to avoid OOM on large files. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { stream ->
            val buf = ByteArray(BUFFER_SIZE)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    /** Compute SHA-256 hex digest of an [InputStream]. Caller must close the stream. */
    fun sha256(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(BUFFER_SIZE)
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
            digest.update(buf, 0, read)
        }
        return digest.digest().toHex()
    }

    /** Compute SHA-256 hex digest of raw [bytes]. */
    fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }

    /** Convert byte array to lower-case hex string. */
    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
