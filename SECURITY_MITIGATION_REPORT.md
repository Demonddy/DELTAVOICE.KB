# Security Mitigation Report — DeltaVoice

**Generated:** Micro-Instruction 4  
**References:** SECURITY_VULNERABILITY_MAP.md, SECURITY_THREAT_MODEL.md  
**Status:** Remediation recommendations — implement in order of priority

---

## Priority Order

| Priority | Severity | Count |
|----------|----------|-------|
| P0 | Critical | 1 |
| P1 | High | 6 |
| P2 | Medium | 9 |
| P3 | Low | 3 |

---

## P0 — Critical

### V1. Hardcoded API Key (SupabaseConfig.kt:20)

**Vulnerability:** JWT anon key embedded in source; extractable via APK decompilation.

**Recommended fix:** Move to BuildConfig or environment; never commit to source control.

**Implementation:**

1. Add to `app/build.gradle`:
```gradle
android {
    defaultConfig {
        buildConfigField "String", "SUPABASE_ANON_KEY", "\"${project.findProperty('SUPABASE_ANON_KEY') ?: ''}\""
    }
}
```

2. Create `local.properties` (gitignored) or use CI secrets:
```properties
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

3. In `gradle.properties` (or CI): add `SUPABASE_ANON_KEY` from secure source.

4. Update `SupabaseConfig.kt`:
```kotlin
object SupabaseConfig {
    val SUPABASE_ANON_KEY: String
        get() = BuildConfig.SUPABASE_ANON_KEY.ifBlank {
            // Fallback for debug; remove in production
            ""
        }
}
```

5. **If key already in production:** Rotate the key in Supabase Dashboard; update all clients.

---

## P1 — High

### V2–V3. Hardcoded URLs (SupabaseConfig.kt, ConvexConfig.kt)

**Vulnerability:** Project URLs in source; enables targeted attacks.

**Recommended fix:** Use BuildConfig for URLs; allow override via env in CI.

**Implementation:**
```gradle
buildConfigField "String", "SUPABASE_URL", "\"${project.findProperty('SUPABASE_URL') ?: 'https://yvizvsojpwgvaisoahda.supabase.co'}\""
```

---

### V4. FileProvider Path Traversal (file_paths.xml)

**Vulnerability:** `path="."` exposes entire cache/files directories.

**Recommended fix:** Restrict to specific subdirectories.

**Implementation:** Replace `res/xml/file_paths.xml` with:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="audio_cache" path="audio/" />
    <external-cache-path name="external_audio_cache" path="audio/" />
    <files-path name="audio_files" path="recordings/" />
</paths>
```

**Note:** Ensure `recordings/` and `audio/` subdirs exist before FileProvider use; create in code if needed.

---

### V5. Backup Enabled (AndroidManifest.xml)

**Vulnerability:** ADB backup can extract SharedPreferences and files.

**Recommended fix:** Use `fullBackupContent` to exclude sensitive data, or disable for release.

**Implementation:**
```xml
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"
    ...>
```

Create `res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="deltavoice_prefs.xml" />
    <exclude domain="sharedpref" path="account_prefs.xml" />
    <exclude domain="database" path="." />
</full-backup-content>
```

Or for maximum security: `android:allowBackup="false"` (loses restore convenience).

---

### V6. Sensitive Data in Plain SharedPreferences (AccountActivity.kt)

**Vulnerability:** user_email, user_name stored unencrypted.

**Recommended fix:** Use EncryptedSharedPreferences for PII.

**Implementation:**

1. Add dependency in `app/build.gradle`:
```gradle
implementation "androidx.security:security-crypto:1.1.0-alpha06"
```

2. Create helper:
```kotlin
// SecurePrefs.kt
object SecurePrefs {
    fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
```

3. In AccountActivity: use `SecurePrefs.create(this)` for PII keys; keep non-sensitive in regular prefs.

---

### V10. Log Leaks (Multiple files)

**Vulnerability:** Log.d/Log.e with user data, API responses, file paths.

**Recommended fix:** Guard all logs with `BuildConfig.DEBUG`; remove or redact in release.

**Implementation:**

1. Create `LogUtils.kt`:
```kotlin
object LogUtils {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) android.util.Log.d(tag, msg)
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) android.util.Log.e(tag, msg, t)
    }
    // ... w, i
}
```

2. Replace all `Log.d("DeltaVoice", ...)` with `LogUtils.d("DeltaVoice", ...)`.

3. **Critical:** Remove or redact any log that contains: user text, API responses, file paths, tokens.

---

### V13–V14. No Certificate Pinning (network_security_config)

**Vulnerability:** MITM possible; no pinning.

**Recommended fix:** Add `network_security_config.xml` with certificate pinning.
See `app/src/main/res/xml/network_security_config.xml` — created in this step.
Add to manifest: `android:networkSecurityConfig="@xml/network_security_config"`.

**See SECURE_CONFIG_GUIDANCE.md** for obtaining and updating certificate pins.

---

## P2 — Medium

### V7–V9. SharedPreferences for Settings (Multiple)

**Vulnerability:** Settings in plain prefs; backup/root access.

**Recommended fix:** For non-PII (keyboard height, sound, etc.): acceptable; ensure backup rules exclude if needed. For any sensitive keys: migrate to EncryptedSharedPreferences.

---

### V11. printStackTrace() (Multiple)

**Vulnerability:** Stack traces in logcat.

**Recommended fix:** Replace with guarded logging:
```kotlin
} catch (e: Exception) {
    if (BuildConfig.DEBUG) e.printStackTrace()
    LogUtils.e("DeltaVoice", "Error: ${e.message}")
}
```

---

### V12. ProGuard Disabled (app/build.gradle)

**Vulnerability:** No obfuscation; easier reverse engineering.

**Recommended fix:** Enable for release:
```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

---

### V15–V16. Intent Extra Path Validation (VoiceProcessModeActivity, VideoConfigActivity)

**Vulnerability:** File paths from intent extras used without validation.

**Recommended fix:** Validate and canonicalize:

```kotlin
private fun validatePath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val file = File(path)
    val canonical = file.canonicalPath
    val appDir = filesDir.canonicalPath
    val cacheDir = cacheDir.canonicalPath
    return canonical.startsWith(appDir) || canonical.startsWith(cacheDir)
}

// Usage:
audioFilePath = intent.getStringExtra(EXTRA_AUDIO_FILE_PATH)
?.takeIf { validatePath(it) }
```

---

### V20–V22. ContentResolver URI Handling (AudioUploadActivity, VideoUploadActivity, VoiceConfigActivity)

**Vulnerability:** content:// URI from GetContent(); validate MIME only.

**Recommended fix:** Validate URI authority and use ContentResolver safely:

```kotlin
private fun isSafeUri(uri: Uri): Boolean {
    val authority = uri.authority ?: return false
    // Allow only known safe providers (media store, etc.)
    return authority.endsWith(".media") || authority == "com.android.providers.media.documents"
}
```

---

## P3 — Low

### V17–V18. Exported Components (IME, MainActivity)

**Status:** Acceptable per design. IME must be exported; MainActivity is launcher. No change.

---

### V19. No FLAG_SECURE (Sensitive screens)

**Vulnerability:** Screenshots/recording on Account, AI Chat.

**Recommended fix:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    setContentView(R.layout.activity_account)
}
```

Apply to: AccountActivity, AIChatConfigActivity, VoiceConfigActivity (if payment/sensitive).

---

### V23. Clipboard Write

**Status:** Expected behavior for clipboard copy. Document in privacy policy; no code change.

---

## Implementation Checklist

| ID | Mitigation | Status |
|----|------------|--------|
| V1 | Move Supabase key to BuildConfig | Pending |
| V2–V3 | Move URLs to BuildConfig | Pending |
| V4 | Restrict FileProvider paths | Pending (see SECURE_CONFIG_GUIDANCE) |
| V5 | fullBackupContent or allowBackup=false | **Implemented** (backup_rules.xml) |
| V6 | EncryptedSharedPreferences for PII | Pending |
| V10 | Guard/remove Log leaks | Pending |
| V11 | Replace printStackTrace | Pending |
| V12 | Enable ProGuard | Pending (rules ready in proguard-rules.pro) |
| V13–V14 | network_security_config + manifest | **Implemented** |
| V15–V16 | Intent path validation | Pending |
| V20–V22 | Content URI validation | Pending |
| V19 | FLAG_SECURE on sensitive screens | Pending |

---

*End of Mitigation Report — Micro-Instruction 4*
