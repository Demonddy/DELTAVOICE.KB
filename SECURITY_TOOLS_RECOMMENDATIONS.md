# Security Tools Recommendations — DeltaVoice

**Generated:** Micro-Instruction 3  
**Purpose:** Tool setup and reference for security testing

---

## 1. OWASP Mobile Security Testing Guide (MSTG)

| MSTG Section | DeltaVoice Relevance |
|--------------|----------------------|
| MSTG-STORAGE-1 | Backup, backup rules |
| MSTG-STORAGE-2 | Sensitive data, SharedPreferences, API keys |
| MSTG-STORAGE-3 | Logging, sensitive data in logs |
| MSTG-STORAGE-9 | FLAG_SECURE, screenshots |
| MSTG-STORAGE-10 | Clipboard |
| MSTG-NETWORK-4 | Certificate pinning, TLS |
| MSTG-ARCH-1 | Exported components |
| MSTG-ARCH-2 | Input validation, path traversal |
| MSTG-CODE-4 | Obfuscation, ProGuard |

**Reference:** https://github.com/OWASP/owasp-mstg

---

## 2. MobSF (Mobile Security Framework)

**Purpose:** Static and dynamic analysis of APK/AAB.

**Setup:**
```bash
# Docker (recommended)
docker pull opensecurity/mobile-security-framework-mobsf
docker run -it -p 8000:8000 opensecurity/mobile-security-framework-mobsf

# Or local install
# https://github.com/MobSF/Mobile-Security-Framework-MobSF
```

**Usage:**
1. Build release APK: `./gradlew assembleRelease`
2. Open MobSF at http://localhost:8000
3. Upload `app/build/outputs/apk/release/app-release.apk`
4. Run static analysis
5. For dynamic: use device/emulator with MobSF agent

**Output:** PDF/JSON report with vulnerabilities, permissions, API analysis.

---

## 3. OWASP Dependency-Check

**Purpose:** Scan dependencies for known CVEs.

**Setup:** Add to `app/build.gradle`:

```gradle
plugins {
    id 'org.owasp.dependencycheck' version '9.0.0' apply false
}
apply plugin: 'org.owasp.dependencycheck'
```

**Usage:**
```bash
./gradlew dependencyCheckAnalyze
```

**Output:** Report in `build/reports/dependency-check-report.html`

---

## 4. Frida

**Purpose:** Runtime hooking, API interception, bypass testing.

**Setup:**
```bash
pip install frida-tools
# On device: frida-server (see Frida docs)
```

**Usage:**
```bash
# List processes
frida-ps -U

# Attach to app
frida -U -f com.deltavoice -l security-test/frida-hooks.js --no-pause
```

**Reference:** https://frida.re/docs/home/

---

## 5. Burp Suite / mitmproxy

**Purpose:** MITM for API traffic, request/response inspection.

**Setup:**
- Burp: Configure proxy (e.g., 8080), install CA cert on device
- mitmproxy: `mitmproxy -p 8080`, install cert on device

**Device proxy:**
- Wi‑Fi → Proxy → Manual → Host: PC IP, Port: 8080

**Usage:**
1. Set device proxy
2. Install CA cert (Burp: http://burp; mitmproxy: http://mitm.it)
3. Use app; inspect traffic in Burp/mitmproxy

---

## 6. ADB Commands

**Purpose:** Backup, logcat, component inspection.

```bash
# Backup (may expose data if allowBackup=true)
adb backup -f backup.ab com.deltavoice

# Logcat (filter logs)
adb logcat -s DeltaVoice

# List packages
adb shell pm list packages | grep deltavoice

# Dump manifest
adb shell dumpsys package com.deltavoice
```

---

## 7. ripgrep (rg) for Static Checks

**Purpose:** Quick pattern search in source.

```bash
# Hardcoded secrets
rg "eyJ|sk-[a-zA-Z0-9]" app/src/main

# Log usage
rg "Log\.(d|i|w|e)\(" app/src/main/java

# SharedPreferences
rg "getSharedPreferences" app/src/main/java

# printStackTrace
rg "printStackTrace" app/src/main
```

---

## 8. Recommended Workflow

1. **Pre-commit:** `rg` / `SecurityTestCases.kt` for secrets, logs
2. **CI:** `dependencyCheckAnalyze` (if plugin added)
3. **Release build:** MobSF static scan
4. **Manual:** Burp/mitmproxy for MITM test
5. **Manual:** Frida for runtime checks (optional)

---

*End of Tools Recommendations — Micro-Instruction 3*
