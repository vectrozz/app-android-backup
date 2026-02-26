package com.zkbackup.app.util

import android.os.Environment
import java.io.File

/**
 * App-wide constants. All tunables live here so they're easy to find and override.
 */
object Constants {

    // ── Upload ────────────────────────────────────────────────
    /** Chunk size in bytes (8 MB). */
    const val CHUNK_SIZE: Long = 8L * 1024 * 1024

    /** Max concurrent chunk uploads per file. */
    const val MAX_PARALLEL_CHUNKS = 2

    /** Max retry attempts for a single chunk. */
    const val MAX_CHUNK_RETRIES = 5

    /** Initial back-off delay in ms (doubles each retry). */
    const val RETRY_BACKOFF_MS = 2_000L

    // ── Crypto ────────────────────────────────────────────────
    /** AES key size in bits. */
    const val AES_KEY_BITS = 256

    /** AES-GCM IV size in bytes. */
    const val GCM_IV_BYTES = 12

    /** AES-GCM tag size in bits. */
    const val GCM_TAG_BITS = 128

    /** Argon2id memory cost in KB (64 MB). */
    const val ARGON2_MEMORY_KB = 65_536

    /** Argon2id iteration count. */
    const val ARGON2_ITERATIONS = 3

    /** Argon2id parallelism. */
    const val ARGON2_PARALLELISM = 1

    /** Salt length in bytes. */
    const val SALT_BYTES = 16

    // ── WorkManager ───────────────────────────────────────────
    /** Periodic scan interval in minutes. */
    const val SCAN_INTERVAL_MINUTES = 15L

    // ── API ───────────────────────────────────────────────────
    const val API_VERSION = "v1"

    // ── Default watch directories (relative to external storage) ──
    val DEFAULT_WATCH_DIRS: List<String> = listOf(
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_DOCUMENTS,
    )
}
