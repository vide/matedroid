# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools.

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Keep data models
-keep class com.matedroid.data.api.models.** { *; }
-keep class com.matedroid.domain.model.** { *; }

# Backup files: the DTOs and the database entities that ship inside one are serialised by
# Moshi, which resolves each generated adapter from the class name at runtime. A restore
# reads files written by other builds, so these names have to survive R8 unchanged.
-keep class com.matedroid.data.backup.** { *; }
-keep class com.matedroid.data.local.entity.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
