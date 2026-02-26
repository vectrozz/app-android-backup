package com.zkbackup.app.crypto

import com.zkbackup.app.util.Constants
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-knowledge crypto manager.
 *
 * - Key derivation: Argon2id(master_password, salt) → 256-bit AES key.
 * - Encryption: AES-256-GCM (authenticated encryption).
 * - One key per user — all files encrypted with the same derived key.
 * - The key and password **never** leave the device.
 */
@Singleton
class CryptoManager @Inject constructor() {

    private val secureRandom = SecureRandom()

    // ── Key derivation ────────────────────────────────────────

    /** Generate a random salt for Argon2. */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(Constants.SALT_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derive a 256-bit AES key from [password] + [salt] using Argon2id.
     *
     * Parameters tuned for mobile: 64 MB memory, 3 iterations, single thread.
     * Takes ~0.5-1s on a mid-range phone.
     */
    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withMemoryAsKB(Constants.ARGON2_MEMORY_KB)
            .withIterations(Constants.ARGON2_ITERATIONS)
            .withParallelism(Constants.ARGON2_PARALLELISM)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val key = ByteArray(Constants.AES_KEY_BITS / 8)
        generator.generateBytes(password.toCharArray(), key)
        return key
    }

    // ── Encryption (streaming, for large files) ───────────────

    /**
     * Encrypt a stream. Writes `[12-byte IV | ciphertext | 16-byte GCM tag]`.
     *
     * Caller is responsible for closing both streams.
     */
    fun encryptStream(key: ByteArray, input: InputStream, output: OutputStream) {
        val iv = ByteArray(Constants.GCM_IV_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)

        // Write IV first (needed for decryption)
        output.write(iv)

        CipherOutputStream(output, cipher).use { cos ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                cos.write(buf, 0, read)
            }
        }
    }

    /**
     * Decrypt a stream. Expects format: `[12-byte IV | ciphertext | GCM tag]`.
     *
     * Returns a [CipherInputStream] — caller reads plaintext from it.
     */
    fun decryptStream(key: ByteArray, input: InputStream): CipherInputStream {
        val iv = ByteArray(Constants.GCM_IV_BYTES)
        val bytesRead = input.readNBytes(iv, 0, iv.size)
        require(bytesRead == Constants.GCM_IV_BYTES) { "Truncated IV" }

        val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
        return CipherInputStream(input, cipher)
    }

    // ── Encryption (in-memory, for small data / chunks) ───────

    /** Encrypt [plaintext] bytes. Returns `[IV | ciphertext | tag]`. */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(Constants.GCM_IV_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv)
        val ciphertext = cipher.doFinal(plaintext)

        return iv + ciphertext   // IV prepended
    }

    /** Decrypt [data] formatted as `[IV | ciphertext | tag]`. Returns plaintext. */
    fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, Constants.GCM_IV_BYTES)
        val ciphertext = data.copyOfRange(Constants.GCM_IV_BYTES, data.size)

        val cipher = createCipher(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(ciphertext)
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun createCipher(mode: Int, key: ByteArray, iv: ByteArray): Cipher {
        val spec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(Constants.GCM_TAG_BITS, iv)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, spec, gcmSpec)
        }
    }

    /** Hex-encode a byte array. */
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /** Hex-decode a string to byte array. */
    fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
