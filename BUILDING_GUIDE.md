# AppManager Build Guide

This guide covers building AppManager locally and via GitHub Actions.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Build](#local-build)
3. [Release Build with Signing](#release-build-with-signing)
4. [GitHub Actions CI/CD](#github-actions-cicd)
5. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

- **JDK 17 or 21** (OpenJDK or Oracle JDK)
- **Android SDK** (API 35 recommended)
- **Android SDK Build-Tools** 35.0.0
- **Git** with submodules support
- **Gradle 8.11+** (included via gradlew)

### Environment Setup

```bash
# Set ANDROID_HOME (Linux/macOS)
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Or on Windows (PowerShell)
$env:ANDROID_HOME = "C:\Users\YourName\AppData\Local\Android\Sdk"
$env:PATH += ";$env:ANDROID_HOME\tools;$env:ANDROID_HOME\platform-tools"
```

---

## Local Build

### 1. Clone the Repository

```bash
git clone --recurse-submodules https://github.com/MuntashirAkon/AppManager.git
cd AppManager
```

### 2. Build Debug APK

```bash
# Make gradlew executable
chmod +x gradlew

# Build debug APK
./gradlew assembleDebug

# Or build universal debug APK (all architectures)
./gradlew packageDebugUniversalApk
```

### 3. Output Location

Debug APKs will be generated at:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk_from_bundle/debug/app-debug-universal.apk`

---

## Release Build with Signing

### 1. Create a Keystore

```bash
keytool -genkey -v -keystore release.keystore \
  -alias appmanager \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Create keystore.properties

Copy `app/keystore.properties.example` to `app/keystore.properties`:

```bash
cp app/keystore.properties.example app/keystore.properties
```

Edit `app/keystore.properties` with your credentials:

```properties
storeFile=../keystore/release.keystore
storePassword=your_keystore_password
keyAlias=appmanager
keyPassword=your_key_password
```

### 3. Build Release APK

```bash
./gradlew assembleRelease
```

### 4. Output Location

Release APKs will be generated at:
- `app/build/outputs/apk/release/app-armeabi-v7a-release.apk`
- `app/build/outputs/apk/release/app-arm64-v8a-release.apk`
- `app/build/outputs/apk/release/app-x86-release.apk`
- `app/build/outputs/apk/release/app-x86_64-release.apk`

---

## GitHub Actions CI/CD

### Workflow Files

AppManager uses GitHub Actions for automated builds:

| Workflow | Trigger | Output |
|----------|---------|--------|
| `debug_build.yml` | Push to master/develop, PRs | Debug APK |
| `release_build.yml` | Git tags (v*), manual | Signed Release |
| `build.yml` | Push to main branches | Debug APK |
| `lint.yml` | Push, PRs | Lint reports |
| `tests.yml` | Push, PRs | Test results |
| `codeql.yml` | Push, schedule | Security analysis |

### Debug Build (Automatic)

Debug builds are automatically triggered on:
- Push to `master`, `main`, or `develop` branches
- Pull requests
- Manual trigger via Actions tab

**Artifacts:**
- Universal debug APK (all architectures)
- Architecture-specific APKs
- Available for 30 days

### Release Build (Manual/Tag)

#### Option 1: Git Tag (Recommended)

```bash
# Tag a release
git tag v4.0.5
git push origin v4.0.5
```

This automatically triggers the release workflow.

#### Option 2: Manual Trigger

1. Go to **Actions** → **Release Build**
2. Click **Run workflow**
3. Enter version number (e.g., `4.0.5`)
4. Check "Create GitHub Release"
5. Click **Run workflow**

### Setting Up Release Signing in GitHub Actions

#### 1. Prepare Keystore

```bash
# Encode keystore to base64
base64 -w 0 release.keystore > keystore.b64
```

#### 2. Add GitHub Secrets

Go to **Settings** → **Secrets and variables** → **Actions** and add:

| Secret Name | Description |
|-------------|-------------|
| `RELEASE_KEYSTORE_B64` | Base64-encoded keystore |
| `KEYSTORE_STORE_PASSWORD` | Keystore password |
| `KEYSTORE_KEY_PASSWORD` | Key password |
| `KEYSTORE_KEY_ALIAS` | Key alias |
| `TELEGRAM_TOKEN` | (Optional) Bot token for notifications |
| `TELEGRAM_TO` | (Optional) Channel/chat ID |
| `FDROID_REPO_TOKEN` | (Optional) F-Droid repo token |

#### 3. Trigger Release

```bash
# Create and push tag
git tag v4.0.5
git push origin v4.0.5
```

The workflow will:
1. Build signed release APKs
2. Create a GitHub Release (draft)
3. Upload all APKs and checksums
4. Send Telegram notification (if configured)

---

## Troubleshooting

### SDK Location Not Found

**Error:** `SDK location not found. Define a valid SDK location`

**Solution:**
1. Create `local.properties` in project root:
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
2. Or set `ANDROID_HOME` environment variable

### Build Fails with Submodules

**Error:** Missing submodule dependencies

**Solution:**
```bash
git submodule update --init --recursive
```

### Out of Memory During Build

**Solution:** Increase Gradle heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m
```

### Signing Errors

**Error:** `Keystore file does not exist`

**Solution:**
1. Ensure `keystore.properties` exists in `app/` directory
2. Verify paths in `keystore.properties` are correct
3. Check that keystore file exists at specified location

### GitHub Actions Failures

**Check:**
1. Workflow file syntax (YAML indentation)
2. Secrets are properly configured
3. Submodules are initialized (`submodules: 'recursive'`)
4. SDK packages are installed

---

## Build Variants

| Variant | Description | Use Case |
|---------|-------------|----------|
| `assembleDebug` | Debug build, signed with debug key | Development, testing |
| `assembleRelease` | Release build, signed with release key | Production |
| `packageDebugUniversalApk` | Universal debug APK | Testing on multiple devices |

---

## Additional Resources

- [BUILDING.rst](BUILDING.rst) - Original build instructions
- [CONTRIBUTING.rst](CONTRIBUTING.rst) - Contribution guidelines
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

---

## Support

For build-related issues:
1. Check existing issues on GitHub
2. Review workflow run logs in Actions tab
3. Open a new issue with build logs attached
