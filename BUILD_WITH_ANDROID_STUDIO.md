# Building APK with Android Studio - Step by Step

This guide shows you exactly how to build an APK file using Android Studio's graphical interface.

## 🎯 Building Debug APK (For Testing)

### Step 1: Open the Project

1. Launch **Android Studio**
2. Click **File** → **Open**
3. Navigate to your project folder: `F:\APPS\keyboard`
4. Click **OK**
5. Wait for Gradle sync to complete (you'll see "Gradle sync finished" at the bottom)

### Step 2: Build the APK

1. In the top menu bar, click **Build**
2. Select **Build Bundle(s) / APK(s)**
3. Click **Build APK(s)**
4. Wait for the build to complete (watch the **Build** tab at the bottom)

### Step 3: Locate the APK

1. When build completes, you'll see a notification: **"APK(s) generated successfully"**
2. Click **locate** in the notification
3. Or manually navigate to: `app\build\outputs\apk\debug\app-debug.apk`

**That's it!** Your debug APK is ready to install.

---

## 🔐 Building Release APK (For Distribution)

Release APKs are signed and optimized. Here's how to build one:

### Step 1: Prepare for Signing

#### Option A: Create New Keystore (First Time)

1. Go to **Build** → **Generate Signed Bundle / APK**
2. Select **APK** → Click **Next**
3. Click **Create new...** button
4. Fill in the keystore form:
   - **Key store path**: Click folder icon, choose location (e.g., `F:\APPS\keyboard\keyboard-release-key.jks`)
   - **Password**: Enter a strong password (remember it!)
   - **Confirm**: Re-enter password
   - **Key alias**: `keyboard-key` (or any name)
   - **Key password**: Enter password (can be same as keystore password)
   - **Validity**: `25` years (default is fine)
   - **Certificate information**:
     - First and Last Name: Your name or company
     - Organizational Unit: Your department (optional)
     - Organization: Your company name
     - City: Your city
     - State: Your state/province
     - Country Code: Your country code (e.g., US, IN, GB)
5. Click **OK**
6. Click **Next**

#### Option B: Use Existing Keystore

1. Go to **Build** → **Generate Signed Bundle / APK**
2. Select **APK** → Click **Next**
3. Click **Choose existing...**
4. Browse and select your `.jks` or `.keystore` file
5. Enter keystore password
6. Select key alias
7. Enter key password
8. Click **Next**

### Step 2: Configure Build

1. **Build Variants**: Select **release**
2. **Signature Versions**:
   - ✅ Check **V1 (Jar Signature)**
   - ✅ Check **V2 (Full APK Signature)** - Recommended
3. Click **Finish**

### Step 3: Wait for Build

- Android Studio will build the signed release APK
- Watch the **Build** tab for progress
- When complete, you'll see: **"APK(s) generated successfully"**

### Step 4: Locate Release APK

- Click **locate** in the notification
- Or navigate to: `app\build\outputs\apk\release\app-release.apk`

---

## 📱 Installing the APK on Your Device

### Method 1: Direct Install via Android Studio

1. Connect your device via USB
2. Enable USB Debugging on device
3. In Android Studio, click **Run** button (▶️) or press `Shift+F10`
4. Select your device
5. App installs automatically

### Method 2: Install APK File Manually

1. **Transfer APK to device**:

   - Copy `app-debug.apk` to your device (USB, email, cloud, etc.)

2. **Enable Unknown Sources** (if needed):

   - Go to **Settings** → **Security** → Enable **"Install from Unknown Sources"**
   - Or **Settings** → **Apps** → **Special Access** → **Install Unknown Apps**
   - Select your file manager app → Enable **"Allow from this source"**

3. **Install**:
   - Open file manager on device
   - Navigate to APK location
   - Tap the APK file
   - Tap **Install**
   - Tap **Open** when done

### Method 3: Install via ADB

1. Connect device via USB
2. Open terminal in Android Studio (bottom tab: **Terminal**)
3. Run:
   ```bash
   adb install app\build\outputs\apk\debug\app-debug.apk
   ```

---

## 🎨 Visual Guide: Menu Locations

### Building Debug APK:

```
Android Studio Menu Bar
  └─ Build
      └─ Build Bundle(s) / APK(s)
          └─ Build APK(s)  ← Click here
```

### Building Release APK:

```
Android Studio Menu Bar
  └─ Build
      └─ Generate Signed Bundle / APK  ← Click here
          └─ Select "APK"
          └─ Click "Next"
          └─ Create/Select Keystore
          └─ Click "Next"
          └─ Select "release"
          └─ Click "Finish"
```

---

## 🔍 Troubleshooting

### "Gradle sync failed"

- **Solution**: Check internet connection (Gradle downloads dependencies)
- **Solution**: Go to **File** → **Invalidate Caches / Restart** → **Invalidate and Restart**
- **Solution**: Check `build.gradle` files for errors

### "Build failed" errors

- **Check**: Look at the **Build** tab (bottom of Android Studio) for error messages
- **Common issues**:
  - Missing dependencies → Check internet connection
  - SDK not found → Go to **Tools** → **SDK Manager** → Install required SDK
  - Syntax errors → Fix code errors shown in red

### "Keystore file not found"

- **Solution**: Make sure you created the keystore file first
- **Solution**: Use absolute path or place keystore in project root

### "APK not found"

- **Check**: `app\build\outputs\apk\debug\` folder
- **Solution**: Build again if folder is empty
- **Note**: First build may take longer (downloading dependencies)

### Build takes too long

- **Normal**: First build downloads all dependencies (5-10 minutes)
- **Subsequent builds**: Should be faster (1-2 minutes)
- **Tip**: Use **Build** → **Rebuild Project** if something seems stuck

---

## ⚡ Quick Tips

1. **Keyboard Shortcuts**:

   - Build: `Ctrl+F9` (Windows/Linux) or `Cmd+F9` (Mac)
   - Run: `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac)

2. **Build Variants**:

   - Switch between debug/release in **Build Variants** tab (left sidebar)
   - Or: **Build** → **Select Build Variant**

3. **View Build Output**:

   - Click **Build** tab at bottom of Android Studio
   - Shows detailed build progress and errors

4. **Clean Build** (if having issues):

   - **Build** → **Clean Project**
   - Then **Build** → **Rebuild Project**

5. **Analyze APK**:
   - **Build** → **Analyze APK**
   - Select your APK file
   - See APK contents, size breakdown, etc.

---

## 📊 APK Information

### Debug APK:

- **Location**: `app\build\outputs\apk\debug\app-debug.apk`
- **Size**: ~2-5 MB typically
- **Signed**: Yes (debug signing key)
- **Use for**: Testing, development

### Release APK:

- **Location**: `app\build\outputs\apk\release\app-release.apk`
- **Size**: ~2-5 MB (can be smaller with ProGuard)
- **Signed**: Yes (your keystore)
- **Use for**: Distribution, production

---

## ✅ Checklist

Before building:

- [ ] Project opens without errors
- [ ] Gradle sync completed successfully
- [ ] No red error markers in code
- [ ] Device connected (if installing directly)

After building:

- [ ] APK file exists in output folder
- [ ] APK size is reasonable (not 0 bytes)
- [ ] Can install APK on device
- [ ] App launches successfully

---

## 🎓 Next Steps

After building your APK:

1. **Test on device**: Install and test all features
2. **Share APK**: Send to testers or upload to Play Store
3. **Optimize**: Enable ProGuard for smaller release APK
4. **Version**: Update version code/name in `build.gradle` for next release

---

**That's everything you need to build APKs in Android Studio!** 🚀

For command-line building, see `BUILD_APK.md` or `QUICK_BUILD.md`.
