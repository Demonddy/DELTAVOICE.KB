# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep IME Service classes (package: com.deltavoice)
-keep class com.deltavoice.MainKeyboardService { *; }
-keep class com.deltavoice.PermissionsActivity { *; }
-keep class com.deltavoice.VideoRecordingActivity { *; }

