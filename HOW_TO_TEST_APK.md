# 📱 How to Test the APK

## ⚡ Quickest Method: Download from GitHub Actions

### Step 1: Go to Actions Tab
https://github.com/thejaustin/AppManager/actions

### Step 2: Find Latest Build
Look for the most recent workflow run with:
- **Workflow**: Debug Build
- **Branch**: `refactor/kotlin-conversion`
- **Status**: ✅ Success

### Step 3: Download APK
1. Click on the workflow run
2. Scroll to **Artifacts** section at bottom
3. Click `AppManager-4.0.6-debug` (or similar name)
4. Download will start (ZIP file)

### Step 4: Install on Device
1. Unzip the downloaded file
2. Transfer APK to your Android device
3. Enable "Install from Unknown Sources"
4. Install the APK
5. Test!

---

## 🖥️ Method 2: Build on PC with Android Studio

### Prerequisites
- Android Studio installed
- JDK 17+
- Android SDK (API 35)

### Steps
```bash
# Clone repository
git clone https://github.com/thejaustin/AppManager.git
cd AppManager

# Checkout branch
git checkout refactor/kotlin-conversion

# Open in Android Studio
# File → Open → Select the project directory

# Wait for Gradle sync

# Set environment variables (or add to gradle.properties)
export DEBUG_STORE_PASSWORD="your_password"
export DEBUG_KEY_PASSWORD="your_password"

# Build → Build Bundle(s) / APK(s) → Build APK(s)
# Or from terminal:
./gradlew assembleDebug
```

### APK Location
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Method 3: Build on Termux (Advanced)

### Prerequisites
```bash
# Install Android SDK command-line tools
pkg install wget unzip openjdk-17

# Download SDK command-line tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip

# Set up SDK
mkdir -p $HOME/android-sdk
mv cmdline-tools $HOME/android-sdk/
cd $HOME/android-sdk/cmdline-tools
mv cmdline-tools latest

# Set environment variables
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Accept licenses
yes | sdkmanager --licenses

# Install required SDK components
sdkmanager "platform-tools"
sdkmanager "platforms;android-35"
sdkmanager "build-tools;35.0.0"
```

### Create local.properties
```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### Build
```bash
cd /data/data/com.termux/files/home/AppManager

# Set credentials
export DEBUG_STORE_PASSWORD="your_password"
export DEBUG_KEY_PASSWORD="your_password"

# Build
./gradlew assembleDebug --no-daemon
```

---

## 🧪 Testing Instructions

### 1. Install APK
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or manually transfer and install on device.

### 2. Follow Testing Checklist
See `TESTING_CHECKLIST.md` for comprehensive testing guide.

### 3. Key Tests

#### Security Fixes
- [ ] Help page loads (Menu → Help)
- [ ] No JavaScript execution in WebView
- [ ] No installation errors
- [ ] LeakCanary shows no leaks (use app for 5+ minutes)

#### M3 Components
- [ ] FAB menu works (if implemented on main page)
- [ ] Loading indicator appears on pull-to-refresh
- [ ] Filter chips work (if visible)
- [ ] Haptic feedback feels right

#### Core Functionality
- [ ] App list loads
- [ ] Search works
- [ ] App details open
- [ ] Archive works
- [ ] Cache cleaner works

### 4. Report Results

Create `TEST_RESULTS.md`:

```markdown
# Test Results - v4.0.6-security

**Tester**: [Your Name]
**Date**: [Date]
**Device**: [Device Model, Android Version]
**Build Method**: [GitHub Actions / Android Studio / Termux]

## Summary
- **Total Tests**: X
- **Passed**: Y
- **Failed**: Z

## Critical Issues
[Any critical bugs]

## Security Tests
- WebView: ✅ Pass / ❌ Fail
- Credentials: ✅ Pass / ❌ Fail  
- Memory Leaks: ✅ Pass / ❌ Fail

## M3 Components
- FAB Menu: ✅ / ❌ / N/A
- Loading Indicator: ✅ / ❌ / N/A
- Filter Chips: ✅ / ❌ / N/A
- Haptics: ✅ / ❌ / N/A

## Performance
- Launch Time: Xs
- Scroll FPS: ~X FPS
- Memory: X MB

## Recommendation
✅ Ready for Release / ❌ Needs Fixes / 🛑 Do Not Release

## Notes
[Any additional comments]
```

---

## 🐛 If You Find Bugs

### Report on GitHub
https://github.com/thejaustin/AppManager/issues/new/choose

### Include:
1. Device model
2. Android version
3. Steps to reproduce
4. Expected behavior
5. Actual behavior
6. Screenshots/logs if applicable

---

## ✅ Ready for Release When:

- [ ] All security tests pass
- [ ] No crashes on launch
- [ ] Core functionality works
- [ ] No memory leaks (LeakCanary)
- [ ] Performance acceptable
- [ ] TEST_RESULTS.md completed
- [ ] Recommendation: "Ready for Release"

---

## 🚫 DO NOT RELEASE If:

- ❌ App crashes on launch
- ❌ Security vulnerabilities found
- ❌ Data loss possible
- ❌ Major regressions
- ❌ Recommendation: "Do Not Release"

---

**Remember**: CI/CD passing is great, but **manual testing is essential** before production release!

Good luck with testing! 🍀
