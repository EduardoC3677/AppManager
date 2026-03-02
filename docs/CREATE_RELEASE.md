# 🚀 How to Create GitHub Release

## Option 1: Automatic (Via Tag Push) ✅ RECOMMENDED

### Step 1: Create and Push Tag
```bash
cd /data/data/com.termux/files/home/AppManager

# Create release candidate tag
git tag -a v4.0.6-rc1 -m "Release Candidate 1"

# Push tag to trigger release workflow
git push origin v4.0.6-rc1
```

### Step 2: Wait for Workflow
1. Go to: https://github.com/thejaustin/AppManager/actions
2. Look for **Release Build** workflow triggered by tag `v4.0.6-rc1`
3. Wait for it to complete (~3-5 minutes)

### Step 3: Download APKs
Once workflow completes:
1. Go to: https://github.com/thejaustin/AppManager/releases
2. Find **v4.0.6-rc1** release
3. Download APKs from Assets section

---

## Option 2: Manual Release (Via GitHub UI)

### Step 1: Go to Releases
https://github.com/thejaustin/AppManager/releases/new

### Step 2: Create Tag
- Click **Choose a tag**
- Type `v4.0.6-rc1`
- Click **Create new tag**

### Step 3: Fill Release Details

**Release Title**:
```
AppManager v4.0.6-rc1 - Security Hardening & M3 Expressive
```

**Description**:
```markdown
## 🔒 Security Improvements

- **WebView Hardened**: XSS and file access vulnerabilities fixed
- **Credentials Removed**: No more hardcoded passwords
- **Memory Leaks Fixed**: GlobalScope replaced with AppScope
- **ANR Prevention**: runBlocking conversion guide created

## 🎨 M3 Expressive Components

- **FAB Menu**: Animated floating action menu
- **Loading Indicator**: Modern linear progress
- **Split Button**: Two-button container with animation
- **Expressive Haptics**: Context-aware feedback
- **Spring Animations**: Natural motion physics

## 🧪 Test Framework

- **LeakCanary**: Memory leak detection enabled
- **Kotlin Tests**: Full Kotlin test support
- **Espresso**: UI testing framework ready

## 📚 Documentation

- Security audit report
- Testing guides
- M3 compliance documentation
- Setup guides

## ⚠️ Important Notes

- This is a **Release Candidate** - needs testing
- Debug build (unsigned)
- Requires Android 10+ (API 29+)
- Report issues: https://github.com/thejaustin/AppManager/issues

## 📥 Installation

1. Download APK matching your device architecture
2. Enable "Install from Unknown Sources"
3. Install and test
4. Report any issues

## 🔗 Links

- [Testing Checklist](https://github.com/thejaustin/AppManager/blob/refactor/kotlin-conversion/TESTING_CHECKLIST.md)
- [Security Issues](https://github.com/thejaustin/AppManager/blob/refactor/kotlin-conversion/SECURITY_ISSUES.md)
- [Build Status](https://github.com/thejaustin/AppManager/actions)
```

### Step 4: Upload APKs
**If you have APKs from local build**:
1. Click **Attach binaries by dropping files here**
2. Drag and drop APK files:
   - `app-debug.apk` (universal)
   - Or architecture-specific APKs

**If using GitHub Actions APKs**:
1. Wait for Release Build workflow to complete
2. APKs will be automatically attached

### Step 5: Set as Pre-release
- ✅ Check **Set as a pre-release**
- This is a release candidate, not final

### Step 6: Publish
- Click **Publish release**

---

## Option 3: Download from Actions (Quickest for Testing)

### Step 1: Go to Actions
https://github.com/thejaustin/AppManager/actions

### Step 2: Find Latest Debug Build
Look for:
- **Workflow**: Debug Build
- **Branch**: `refactor/kotlin-conversion`
- **Status**: ✅ Success

### Step 3: Download Artifact
1. Click on workflow run
2. Scroll to **Artifacts** section
3. Click `AppManager-*-debug` to download

### Step 4: Install
1. Unzip downloaded file
2. Transfer APK to device
3. Install and test

---

## Current Status

### Tags Created
- ✅ `v4.0.6-rc1` - Release Candidate 1 (created)

### Workflows to Watch
- **Release Build** - Creates release with APKs
- **Debug Build** - Creates debug APKs for testing

### Where to Check
- **Releases**: https://github.com/thejaustin/AppManager/releases
- **Actions**: https://github.com/thejaustin/AppManager/actions
- **Tags**: https://github.com/thejaustin/AppManager/tags

---

## After Release

### Monitor For:
- [ ] Release workflow completes successfully
- [ ] APKs attached to release
- [ ] No build errors
- [ ] All architectures built

### Test and Report:
- [ ] Install on your device
- [ ] Follow TESTING_CHECKLIST.md
- [ ] Report any bugs
- [ ] Update TEST_RESULTS.md

### If Everything Works:
- [ ] Create final v4.0.6 tag
- [ ] Publish as stable release
- [ ] Announce in changelog

### If Issues Found:
- [ ] Fix bugs
- [ ] Create v4.0.6-rc2 tag
- [ ] Repeat testing

---

## Quick Links

| Resource | URL |
|----------|-----|
| **Releases** | https://github.com/thejaustin/AppManager/releases |
| **Actions** | https://github.com/thejaustin/AppManager/actions |
| **Tags** | https://github.com/thejaustin/AppManager/tags |
| **Testing Guide** | TESTING_CHECKLIST.md |
| **Security Issues** | SECURITY_ISSUES.md |

---

**Created**: March 1, 2026  
**Tag**: v4.0.6-rc1  
**Status**: ⏳ Pending Release Workflow
