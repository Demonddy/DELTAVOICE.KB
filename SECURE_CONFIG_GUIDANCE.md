# Secure Configuration Guidance — DeltaVoice

**Generated:** Micro-Instruction 4  
**Purpose:** Step-by-step implementation guide for security hardening

---

## 1. Secrets Management (V1, V2, V3)

### BuildConfig for API Key and URLs

**Step 1:** Add to `app/build.gradle` inside `android { defaultConfig { } }`:

```gradle
buildConfigField "String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY") ?: ""}\""
buildConfigField "String", "SUPABASE_URL", "\"${project.findProperty("SUPABASE_URL") ?: "https://yvizvsojpwgvaisoahda.supabase.co"}\""
```

**Step 2:** Create `gradle.properties` (or use CI secrets) — **do not commit real keys**:

```properties
# For local dev only; use CI secrets in production
SUPABASE_ANON_KEY=your_key_here
```

**Step 3:** Update `SupabaseConfig.kt`:

```kotlin
object SupabaseConfig {
    val SUPABASE_URL: String get() = BuildConfig.SUPABASE_URL
    val SUPABASE_ANON_KEY: String get() = BuildConfig.SUPABASE_ANON_KEY
    // ... rest unchanged
}
```

**Step 4:** Add `gradle.properties` with secrets to `.gitignore` or use `local.properties`.

---

## 2. Certificate Pinning (V13, V14)

### Obtaining Certificate Pins

Run for each domain (Supabase, Convex, etc.):

```bash
# Replace DOMAIN with e.g. yvizvsojpwgvaisoahda.supabase.co
echo | openssl s_client -servername DOMAIN -connect DOMAIN:443 2>/dev/null | \
  openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | openssl enc -base64
```

**Windows (PowerShell):**
```powershell
$domain = "yvizvsojpwgvaisoahda.supabase.co"
$tcp = New-Object System.Net.Sockets.TcpClient($domain, 443)
# Use OpenSSL if available, or: https://www.ssllabs.com/ssltest/analyze.html for cert chain
```

**Alternative:** Use [SSL Labs](https://www.ssllabs.com/ssltest/) or [report-uri pin generator](https://report-uri.com/home/pkp_hash).

### Updating network_security_config.xml

1. Uncomment the `<domain-config>` block in `app/src/main/res/xml/network_security_config.xml`.
2. Replace `REPLACE_WITH_ACTUAL_PIN` and `REPLACE_WITH_BACKUP_PIN` with base64 pins.
3. Use a backup pin from a different cert (e.g., backup CA) for rotation.
4. Set `expiration` to cert expiry (e.g., 90 days before renewal).

---

## 3. EncryptedSharedPreferences (V6)

### Dependency

```gradle
implementation "androidx.security:security-crypto:1.1.0-alpha06"
```

### Migration Pattern

```kotlin
// Create secure prefs for PII
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
val securePrefs = EncryptedSharedPreferences.create(
    context, "secure_account_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// Migrate: read from old prefs, write to secure, then clear old
val oldPrefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
securePrefs.edit()
    .putString(KEY_USER_EMAIL, oldPrefs.getString(KEY_USER_EMAIL, null))
    .putString(KEY_USER_NAME, oldPrefs.getString(KEY_USER_NAME, null))
    .apply()
oldPrefs.edit().remove(KEY_USER_EMAIL).remove(KEY_USER_NAME).apply()
```

---

## 4. FileProvider Path Tightening (V4)

**Current:** `path="."` exposes entire cache/files dirs.

**Target:** Use specific subdirs. Requires app refactor to write files under subdirs.

**Step 1:** Refactor to use:
- `cacheDir/audio/` for processed audio
- `cacheDir/recordings/` for recordings
- `filesDir/recordings/` for uploads
- `filesDir/videos/` for videos

**Step 2:** Update `res/xml/file_paths.xml`:

```xml
<paths>
    <cache-path name="audio_cache" path="audio/" />
    <cache-path name="cache_recordings" path="recordings/" />
    <cache-path name="cache_videos" path="videos/" />
    <files-path name="audio_files" path="recordings/" />
    <files-path name="video_files" path="videos/" />
</paths>
```

**Step 3:** Update all `File(cacheDir, "filename")` to `File(File(cacheDir, "audio"), "filename")` etc.

---

## 5. ProGuard/R8 (V12)

**In `app/build.gradle`:**

```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

**Keep rules:** See `proguard-rules.pro` — IME, config classes, and serialization models must be kept.

---

## 6. Log Guarding (V10, V11)

**Create `LogUtils.kt`:**

```kotlin
object LogUtils {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, msg, t)
    }
}
```

**Replace:** All `Log.d/e/w` → `LogUtils.d/e/w`. Remove `printStackTrace()` or wrap in `if (BuildConfig.DEBUG)`.

---

## 7. Intent Path Validation (V15, V16)

**Helper:**

```kotlin
private fun isPathInAppStorage(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    return try {
        val file = File(path)
        val canonical = file.canonicalPath
        canonical.startsWith(filesDir.canonicalPath) ||
            canonical.startsWith(cacheDir.canonicalPath) ||
            (getExternalFilesDir(null)?.canonicalPath?.let { canonical.startsWith(it) } == true)
    } catch (_: Exception) { false }
}
```

**Usage:** `intent.getStringExtra(EXTRA_AUDIO_FILE_PATH)?.takeIf { isPathInAppStorage(it) }`

---

## 8. FLAG_SECURE (V19)

**In AccountActivity, AIChatConfigActivity, VoiceConfigActivity `onCreate`:**

```kotlin
window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
```

---

## 9. android:exported Audit

| Component | Exported | Action |
|-----------|----------|--------|
| MainKeyboardService | true | Required for IME; keep |
| MainActivity | true | Launcher; keep |
| All other activities | false | OK |
| FileProvider | false | OK |

---

## 10. Backup Rules (V5)

**Implemented:** `res/xml/backup_rules.xml` excludes `deltavoice_prefs.xml`.

**To exclude more:** Add lines:
```xml
<exclude domain="sharedpref" path="audio_upload_prefs.xml" />
<exclude domain="sharedpref" path="video_upload_prefs.xml" />
```

---

*End of Secure Config Guidance*
