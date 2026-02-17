# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep IME Service classes
-keep class com.keyboard.app.MainKeyboardService { *; }
-keep class com.keyboard.app.PermissionsActivity { *; }
-keep class com.keyboard.app.VideoRecordingActivity { *; }

