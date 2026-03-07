/**
 * Security Test Cases — DeltaVoice
 *
 * Reference implementation for static security checks.
 * Run manually from project root. Not part of the app build.
 *
 * Usage: kotlinc -script SecurityTestCases.kt
 * Or: copy logic into IDE scratch file / CI script
 */

import java.io.File

// Adjust if run from different location
private const val APP_SRC = "app/src/main"
private const val APP_JAVA = "$APP_SRC/java"
private const val APP_RES = "$APP_SRC/res"
private const val MANIFEST = "$APP_SRC/AndroidManifest.xml"

fun main() {
    println("=== DeltaVoice Security Checks (Reference) ===\n")
    var failed = 0

    // 1. Hardcoded secrets
    if (checkHardcodedSecrets()) {
        println("[FAIL] 1.1 Hardcoded secrets (JWT/sk- pattern) found")
        failed++
    } else {
        println("[PASS] 1.1 No hardcoded JWT/sk- patterns")
    }

    // 2. EncryptedSharedPreferences
    if (!checkEncryptedPrefs()) {
        println("[FAIL] 2.1 Sensitive data may use plain SharedPreferences")
        failed++
    } else {
        println("[PASS] 2.1 EncryptedSharedPreferences used for PII")
    }

    // 3. network_security_config
    if (!File("$APP_RES/xml/network_security_config.xml").exists()) {
        println("[FAIL] 3.1 network_security_config.xml missing")
        failed++
    } else {
        println("[PASS] 3.1 network_security_config.xml exists")
    }

    // 4. FileProvider path="."
    if (checkFileProviderPaths()) {
        println("[FAIL] 4.1 FileProvider uses path=\".\" (broad exposure)")
        failed++
    } else {
        println("[PASS] 4.1 FileProvider paths restricted")
    }

    // 5. Log.* in source
    val logCount = countLogCalls()
    if (logCount > 0) {
        println("[WARN] 5.1 Found $logCount Log.d/i/w/e calls (review for release)")
    } else {
        println("[PASS] 5.1 No Log calls in source")
    }

    // 6. printStackTrace
    val stackCount = countPrintStackTrace()
    if (stackCount > 0) {
        println("[WARN] 5.2 Found $stackCount printStackTrace() calls")
    }

    // 7. allowBackup
    if (checkAllowBackup()) {
        println("[WARN] 6.1 android:allowBackup=\"true\" (consider fullBackupContent)")
    }

    println("\n=== Done. Failures: $failed ===")
}

private fun checkHardcodedSecrets(): Boolean {
    val patterns = listOf("eyJ", "sk-", "SUPABASE_ANON_KEY = \"")
    val dir = File(APP_JAVA)
    if (!dir.exists()) return false
    return dir.walkTopDown()
        .filter { it.extension == "kt" || it.extension == "java" }
        .any { file ->
            val text = file.readText()
            patterns.any { text.contains(it) }
        }
}

private fun checkEncryptedPrefs(): Boolean {
    val dir = File(APP_JAVA)
    if (!dir.exists()) return false
    val hasEncrypted = dir.walkTopDown()
        .filter { it.extension == "kt" }
        .any { it.readText().contains("EncryptedSharedPreferences") }
    val hasPlainPii = dir.walkTopDown()
        .filter { it.extension == "kt" }
        .any { it.readText().contains("KEY_USER_EMAIL") && it.readText().contains("getSharedPreferences") }
    return hasEncrypted || !hasPlainPii
}

private fun checkFileProviderPaths(): Boolean {
    val pathsFile = File("$APP_RES/xml/file_paths.xml")
    if (!pathsFile.exists()) return false
    return pathsFile.readText().contains("path=\".\"")
}

private fun countLogCalls(): Int {
    val dir = File(APP_JAVA)
    if (!dir.exists()) return 0
    return dir.walkTopDown()
        .filter { it.extension == "kt" || it.extension == "java" }
        .sumOf { file ->
            Regex("""Log\.(d|i|w|e)\s*\(""").findAll(file.readText()).count()
        }
}

private fun countPrintStackTrace(): Int {
    val dir = File(APP_JAVA)
    if (!dir.exists()) return 0
    var count = 0
    dir.walkTopDown()
        .filter { it.extension == "kt" || it.extension == "java" }
        .forEach { file ->
            count += file.readText().split("printStackTrace").size - 1
        }
    return count.coerceAtLeast(0)
}

private fun checkAllowBackup(): Boolean {
    val manifest = File(MANIFEST)
    if (!manifest.exists()) return false
    return manifest.readText().contains("allowBackup=\"true\"")
}
