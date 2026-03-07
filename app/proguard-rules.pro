# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
# See SECURE_CONFIG_GUIDANCE.md for security hardening.

# --- IME & Core ---
-keep class com.deltavoice.MainKeyboardService { *; }
-keep class com.deltavoice.PermissionsActivity { *; }
-keep class com.deltavoice.VideoRecordingActivity { *; }

# --- Config (keep for BuildConfig / runtime access) ---
-keep class com.deltavoice.config.** { *; }

# --- Kotlin Serialization (Supabase, API models) ---
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }

# --- Supabase / Ktor ---
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class io.github.jan.supabase.** { *; }

# --- Remove debug/info logs in release (keep errors) ---
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

