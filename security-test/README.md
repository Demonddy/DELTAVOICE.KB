# Security Test Artifacts

**Purpose:** Optional, manual security testing tools for the DeltaVoice keyboard app.  
**Status:** Reference implementations — not part of the main build.

## Contents

| File | Description |
|------|-------------|
| `SecurityTestCases.kt` | Kotlin reference for static checks (run manually from project root) |
| `frida-hooks.js` | Frida script stubs for runtime testing (requires Frida + rooted device/emulator) |
| `burp-checklist.txt` | Burp Suite test items for API interception |

## Usage

- **Do not** add these to the app's build or test source sets.
- Run only in a controlled test environment.
- See `SECURITY_TOOLS_RECOMMENDATIONS.md` for tool setup.

## Running SecurityTestCases.kt

**Must run from project root** (paths are relative to project root):

```bash
# Option 1: Compile and run (requires Kotlin)
cd /path/to/keyboard
kotlinc security-test/SecurityTestCases.kt -include-runtime -d security-test/SecurityTestCases.jar
java -jar security-test/SecurityTestCases.jar

# Option 2: Copy logic into a scratch file and run from IDE
# Option 3: Use the grep/ripgrep commands from SECURITY_PENTEST_CHECKLIST.md
```

## Ethical Use

- Test only on your own devices and data.
- Do not use against production user data without authorization.
