# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.**

# ---- Moshi ----
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-dontwarn com.squareup.moshi.**

# ---- OkHttp / network ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ---- Apache Commons (Net, Compress) ----
-dontwarn org.apache.commons.**
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.net.** { *; }

# ---- JSch (SFTP) ----
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ---- SMBJ & MBassador ----
-keep class com.hierynomus.smbj.** { *; }
-keep class net.engio.mbassy.** { *; }
-dontwarn com.hierynomus.**
-dontwarn com.rapid7.**
-dontwarn net.engio.mbassy.**
-dontwarn javax.el.**

# ---- Auto-Updater DTOs (Moshi) ----
-keep class com.example.core.update.** { *; }

# ---- XZ / Tukaani ----
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**

# ---- Coil ----
-dontwarn coil.**

# ---- Kotlin coroutines ----
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---- Keep DataStore / proto names ----
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---- Google Play Services Auth ----
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }
