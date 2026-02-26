package com.zkbackup.app.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zkbackup.app.data.preferences.AppPreferences
import com.zkbackup.app.di.TrustAllClient
import com.zkbackup.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level HTTP client for the ZK Backup server REST API.
 *
 * All endpoints are under `/api/v1/...`. Authentication is via Bearer JWT
 * in the Authorization header — the token is read from [AppPreferences].
 *
 * When the user has "self-hosted" mode enabled (IP-based server with self-signed
 * cert), the [trustAllClient] is used instead of the default [defaultClient].
 */
@Singleton
class ApiService @Inject constructor(
    private val defaultClient: OkHttpClient,
    @TrustAllClient private val trustAllClient: OkHttpClient,
    private val prefs: AppPreferences,
    private val gson: Gson
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val binaryType = "application/octet-stream".toMediaType()

    /** Pick the right OkHttpClient based on user preference. */
    private suspend fun httpClient(): OkHttpClient =
        if (prefs.getSelfHostedOnce()) trustAllClient else defaultClient

    private suspend fun baseUrl(): String =
        prefs.getServerUrlOnce().trimEnd('/')

    private suspend fun authHeader(): String =
        "Bearer ${prefs.getAccessTokenOnce()}"

    // ── Auth ──────────────────────────────────────────────────

    data class AuthResponse(val access_token: String, val refresh_token: String)
    data class DeviceResponse(val device_id: String)

    /** Register a new account. Returns JWT tokens. */
    suspend fun register(email: String, password: String): AuthResponse =
        post("/api/${Constants.API_VERSION}/auth/register",
            mapOf("email" to email, "password" to password), authenticated = false)

    /** Login. Returns JWT tokens. */
    suspend fun login(email: String, password: String): AuthResponse =
        post("/api/${Constants.API_VERSION}/auth/login",
            mapOf("email" to email, "password" to password), authenticated = false)

    /** Refresh access token using refresh token. */
    suspend fun refreshToken(refreshToken: String): AuthResponse =
        post("/api/${Constants.API_VERSION}/auth/refresh",
            mapOf("refresh_token" to refreshToken), authenticated = false)

    /** Register this device. Returns device ID. */
    suspend fun registerDevice(name: String): DeviceResponse =
        post("/api/${Constants.API_VERSION}/auth/devices",
            mapOf("name" to name), authenticated = true)

    // ── Upload protocol ───────────────────────────────────────

    data class UploadInitResponse(val upload_id: String, val already_exists: Boolean)
    data class UploadStatusResponse(
        val upload_id: String,
        val status: String,
        val chunks_received: List<Int>
    )

    /**
     * Initialize a file upload. Server returns an upload_id.
     * If the file hash already exists server-side, [UploadInitResponse.already_exists] is true.
     */
    suspend fun uploadInit(
        fileHash: String,
        encryptedSize: Long,
        chunkCount: Int,
        deviceId: String
    ): UploadInitResponse =
        post("/api/${Constants.API_VERSION}/upload/init", mapOf(
            "file_hash" to fileHash,
            "encrypted_size" to encryptedSize,
            "chunk_count" to chunkCount,
            "device_id" to deviceId
        ), authenticated = true)

    /** Upload a single chunk (binary body). */
    suspend fun uploadChunk(uploadId: String, chunkIndex: Int, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl()}/api/${Constants.API_VERSION}/upload/$uploadId/chunk/$chunkIndex"
            val body = data.toRequestBody(binaryType)
            val request = Request.Builder()
                .url(url)
                .put(body)
                .addHeader("Authorization", authHeader())
                .build()

            httpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Chunk upload failed: ${response.code}")
                true
            }
        }

    /** Mark upload as complete. Server verifies all chunks received. */
    suspend fun uploadComplete(uploadId: String): Boolean {
        val response: Map<String, Any> = post(
            "/api/${Constants.API_VERSION}/upload/$uploadId/complete",
            emptyMap<String, Any>(),
            authenticated = true
        )
        return response["status"] == "complete"
    }

    /** Check upload status (which chunks are already received). */
    suspend fun uploadStatus(uploadId: String): UploadStatusResponse =
        get("/api/${Constants.API_VERSION}/upload/$uploadId/status", authenticated = true)

    // ── Generic HTTP helpers ──────────────────────────────────

    private suspend inline fun <reified T> post(
        path: String,
        body: Any,
        authenticated: Boolean
    ): T = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}$path"
        val json = gson.toJson(body).toRequestBody(jsonType)
        val builder = Request.Builder().url(url).post(json)
        if (authenticated) builder.addHeader("Authorization", authHeader())

        httpClient().newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("$path failed: ${response.code} ${response.body?.string()}")
            }
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(response.body!!.string(), type)
        }
    }

    private suspend inline fun <reified T> get(
        path: String,
        authenticated: Boolean
    ): T = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}$path"
        val builder = Request.Builder().url(url).get()
        if (authenticated) builder.addHeader("Authorization", authHeader())

        httpClient().newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("$path failed: ${response.code} ${response.body?.string()}")
            }
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(response.body!!.string(), type)
        }
    }
}
