# Add project specific ProGuard rules here.
# See https://developer.android.com/studio/build/shrink-code for details.

# Room generated code
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
