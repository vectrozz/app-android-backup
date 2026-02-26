package com.zkbackup.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.zkbackup.app.data.db.AppDatabase
import com.zkbackup.app.data.db.dao.BackupFileDao
import com.zkbackup.app.data.db.dao.ChunkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** OkHttpClient that trusts all certificates (for self-hosted servers with self-signed certs). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TrustAllClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Room database ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "zkbackup.db")
            .fallbackToDestructiveMigration()   // acceptable for v1 — add proper migrations later
            .build()

    @Provides
    fun provideBackupFileDao(db: AppDatabase): BackupFileDao = db.backupFileDao()

    @Provides
    fun provideChunkDao(db: AppDatabase): ChunkDao = db.chunkDao()

    // ── Gson ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    // ── OkHttp ────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)   // large chunk uploads
            .addInterceptor(logging)
            .build()
    }

    /**
     * OkHttpClient that trusts ALL certificates.
     * Used ONLY when the user enables "Self-hosted server" in setup
     * (i.e. connecting to an IP with a Caddy self-signed cert).
     */
    @Provides
    @Singleton
    @TrustAllClient
    fun provideTrustAllOkHttpClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }
}
