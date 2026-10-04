# Add project specific ProGuard rules here.
# See https://developer.android.com/studio/build/shrink-code for details.

# Room generated code
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Convex client: its Rust core is reached through JNA/UniFFI by reflection.
-keep class dev.convex.** { *; }
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**

# Convex query results are decoded into these @Serializable DTOs.
-keep class com.cornguard.app.data.remote.convex.** { *; }
