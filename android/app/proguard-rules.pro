# Proguard rules for ZK Backup

# Keep BouncyCastle classes (Argon2)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }

# Keep Room entities
-keep class com.zkbackup.app.data.db.entity.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
