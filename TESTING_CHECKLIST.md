# 📱 Pre-Production Testing Checklist

## ⚠️ IMPORTANT: DO NOT RELEASE WITHOUT TESTING

**Build**: v4.0.6-security  
**Branch**: `refactor/kotlin-conversion`  
**Commit**: `2b4c6312e`  
**Test Status**: ⏳ **PENDING MANUAL TESTING**

---

## 🔧 Step 1: Build Test APK

### Option A: Debug APK (Quick Test)
```bash
cd /data/data/com.termux/files/home/AppManager

# Set environment variables
export DEBUG_STORE_PASSWORD=your_password
export DEBUG_KEY_PASSWORD=your_password

# Build debug APK
./gradlew assembleDebug

# APK location
ls -lh app/build/outputs/apk/debug/
```

### Option B: Universal Debug APK (All Architectures)
```bash
./gradlew packageDebugUniversalApk

# APK location
ls -lh app/build/outputs/apk_from_bundle/debug/
```

### Option C: Release APK (Production Test)
```bash
# Need signing credentials first
# See MCP_SETUP.md for setup

./gradlew assembleRelease
```

---

## ✅ Step 2: Install & Basic Tests

### Installation
- [ ] APK installs successfully
- [ ] No installation errors
- [ ] App icon appears
- [ ] App opens without crash

### Launch Tests
- [ ] Splash screen appears
- [ ] Main page loads
- [ ] App list displays
- [ ] No immediate crashes

---

## 🔒 Step 3: Security Fix Verification

### WebView Security (HelpActivity)
**File**: `HelpActivity.kt`

**Test Steps**:
1. Open App → Menu → Help/User Manual
2. Try to execute JavaScript (should not work)
3. Try to access file:// URLs (should be blocked)
4. Verify help content loads correctly

**Expected**:
- ✅ Help page loads
- ✅ No JavaScript execution
- ✅ No file access
- ✅ No crashes

**Status**: ⏳ Not Tested

---

### Environment Variables (Build Configuration)
**File**: `build.gradle`

**Test Steps**:
1. Build without environment variables set
2. Verify build fails gracefully
3. Set environment variables
4. Verify build succeeds

**Expected**:
- ✅ Build fails without credentials (error message)
- ✅ Build succeeds with credentials
- ✅ No passwords in git history

**Status**: ⏳ Not Tested

---

### Memory Leak Prevention (AppScope)
**File**: `PackageInstallReceiver.kt`

**Test Steps**:
1. Install/uninstall multiple apps
2. Watch LeakCanary notifications
3. Check for memory leak alerts

**Expected**:
- ✅ No memory leaks from PackageInstallReceiver
- ✅ LeakCanary shows no leaks (or only expected ones)

**Status**: ⏳ Not Tested

---

## 🎨 Step 4: M3 Expressive Components

### FAB Menu
**Test Steps**:
1. Open main page
2. Look for FAB (Floating Action Button)
3. Tap FAB to open menu
4. Verify menu items appear
5. Tap menu items
6. Verify haptic feedback

**Expected**:
- ✅ FAB visible
- ✅ Menu opens with animation
- ✅ Spring animation feels smooth
- ✅ Haptic feedback on interactions
- ✅ Menu closes properly

**Status**: ⏳ Not Tested

---

### Loading Indicator
**Test Steps**:
1. Pull to refresh on main page
2. Verify loading indicator appears
3. Check animation smoothness
4. Verify it disappears after refresh

**Expected**:
- ✅ Linear progress indicator (not circular)
- ✅ Smooth animation
- ✅ Disappears after load
- ✅ No visual glitches

**Status**: ⏳ Not Tested

---

### Filter Chips
**Test Steps**:
1. Open main page
2. Look for filter chips at top
3. Tap different filters
4. Verify app list filters correctly

**Expected**:
- ✅ Filter chips visible
- ✅ One-tap filtering works
- ✅ Visual feedback on tap
- ✅ List updates correctly

**Status**: ⏳ Not Tested

---

### Haptic Feedback
**Test Steps**:
1. Enable haptics in system settings
2. Tap various buttons
3. Long press items
4. Scroll through lists

**Expected**:
- ✅ Light haptic on button taps
- ✅ Medium haptic on important actions
- ✅ Heavy haptic on destructive actions
- ✅ Not too intense

**Status**: ⏳ Not Tested

---

## 🧪 Step 5: Core Functionality

### App List
- [ ] Apps display correctly
- [ ] Scroll is smooth
- [ ] Fast scroll works
- [ ] Search works
- [ ] Filter works

### App Details
- [ ] Tap app opens details
- [ ] All tabs load
- [ ] Components display
- [ ] Permissions display

### Archive Function
- [ ] Archive works
- [ ] Archived apps list
- [ ] Restore works

### Cache Cleaner
- [ ] Opens from menu
- [ ] Shows cache sizes
- [ ] Clean all works
- [ ] Individual clean works

---

## 🐛 Step 6: Bug Hunt

### Common Issues
- [ ] No ANR (App Not Responding)
- [ ] No crashes
- [ ] No UI freezes
- [ ] No memory leaks (LeakCanary)
- [ ] No battery drain

### Edge Cases
- [ ] Works with 0 apps
- [ ] Works with 1000+ apps
- [ ] Works in landscape
- [ ] Works on tablet (if available)
- [ ] Works in dark mode
- [ ] Works in light mode

---

## 📊 Step 7: Performance

### Launch Time
- [ ] Cold start: < 2 seconds
- [ ] Warm start: < 1 second

### Scroll Performance
- [ ] 60 FPS while scrolling
- [ ] No jank
- [ ] Fast scroll responsive

### Memory Usage
- [ ] No continuous memory growth
- [ ] GC doesn't cause stutter
- [ ] LeakCanary shows no leaks

---

## 📝 Step 8: Document Results

### Create Test Report
Copy this to `TEST_RESULTS.md`:

```markdown
# Test Results - v4.0.6-security

**Tester**: [Your Name]
**Date**: [Date]
**Device**: [Device Model, Android Version]

## Summary
- Total Tests: X
- Passed: Y
- Failed: Z
- Skipped: W

## Critical Issues
[List any critical bugs found]

## Security Tests
- WebView: ✅/❌
- Credentials: ✅/❌
- Memory Leaks: ✅/❌

## M3 Components
- FAB Menu: ✅/❌
- Loading Indicator: ✅/❌
- Filter Chips: ✅/❌
- Haptics: ✅/❌

## Performance
- Launch Time: Xs
- Scroll FPS: ~X FPS
- Memory: X MB

## Recommendation
[Ready for Release / Needs Fixes / Do Not Release]
```

---

## 🚀 Step 9: Decision

### Ready for Release If:
- ✅ All security tests pass
- ✅ No critical bugs
- ✅ No memory leaks
- ✅ Core functionality works
- ✅ Performance acceptable

### Needs Fixes If:
- ❌ Security tests fail
- ❌ Critical bugs found
- ❌ Memory leaks detected
- ❌ Core functionality broken

### Do Not Release If:
- ❌ App crashes on launch
- ❌ Security vulnerabilities
- ❌ Data loss possible
- ❌ Major regressions

---

## 📱 Test Devices

### Minimum Requirements
- Android 10+ (API 29+)
- 2GB RAM
- 16GB storage

### Recommended Test Devices
- [ ] Pixel 8 Pro (Android 15)
- [ ] Samsung S24 (OneUI 6)
- [ ] OnePlus 12 (OxygenOS 14)
- [ ] Tablet (large screen test)
- [ ] Foldable (if available)

---

## 🔗 Resources

### Documentation
- `SECURITY_ISSUES.md` - What was fixed
- `docs/TESTING_GUIDE.md` - How to test
- `FINAL_SUMMARY.md` - What changed

### Support
- Report issues: https://github.com/thejaustin/AppManager/issues
- Discussions: https://github.com/thejaustin/AppManager/discussions

---

**Testing Started**: [Date]  
**Testing Completed**: [Date]  
**Ready for Release**: [Yes/No]

---

*Remember: CI/CD passing ≠ Production Ready. Always test manually!*
