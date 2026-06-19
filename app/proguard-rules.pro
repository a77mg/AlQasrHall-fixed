# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Prevent ProGuard/R8 optimizations which are slow, complex, and can break runtime class instantiation
-dontoptimize

# Prevent obfuscation and shrinking of our application classes
-keep class com.alqasrhall.booking.** { *; }

# Keep all Room database entity classes perfectly intact
-keep @androidx.room.Entity class * { *; }

# Maintain fully readable stack traces for crash reporting
-keepattributes SourceFile,LineNumberTable

# Keep custom serializable classes and Room/Database/Moshi models
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class * implements java.io.Serializable { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Firebase SDK classes intact to avoid structural breakdown
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Prevent shrinking of Jetpack Compose runtime classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
