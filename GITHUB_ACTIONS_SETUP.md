# 🚀 GitHub Actions Setup Guide

## ✅ What's Already Done

- [x] Code committed and pushed to `refactor/kotlin-conversion` branch
- [x] Release tag `v4.0.6-ux-improvements` created and pushed
- [x] GitHub Actions workflows configured:
  - `debug_build.yml` - Auto-triggers on push/PR
  - `release_build.yml` - Triggers on tags or manually

## 🔐 Required GitHub Secrets

### For Signed Release Builds (Optional but Recommended)

Go to **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `RELEASE_KEYSTORE_B64` | Base64-encoded keystore file | Your release keystore encoded: `base64 -w 0 release.keystore` |
| `KEYSTORE_STORE_PASSWORD` | Your keystore password | Store password from keystore.properties |
| `KEYSTORE_KEY_PASSWORD` | Your key password | Key password from keystore.properties |
| `KEYSTORE_KEY_ALIAS` | Your key alias | Key alias from keystore.properties |

### For Telegram Notifications (Optional)

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `TELEGRAM_TOKEN` | Bot token from @BotFather | Create bot via Telegram |
| `TELEGRAM_TO` | Channel/Chat ID | Where to send notifications |

### For F-Droid Repo (Optional)

| Secret Name | Value | Description |
|-------------|-------|-------------|
| `FDROID_REPO_TOKEN` | GitHub token with repo access | For auto-deploy to F-Droid repo |

## 📱 Creating a Release Keystore

If you don't have a release keystore:

```bash
# Generate new keystore
keytool -genkey -v \
  -keystore release.keystore \
  -alias appmanager \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Encode for GitHub Secrets
base64 -w 0 release.keystore > keystore.b64
# Copy the output to RELEASE_KEYSTORE_B64 secret
```

## 🏗️ How to Trigger Builds

### Debug Build (Automatic)

Debug builds trigger automatically on:
- Push to `master`, `main`, `develop` branches
- Pull requests
- Manual trigger from Actions tab

**Manual Trigger:**
1. Go to **Actions** → **Debug Build**
2. Click **Run workflow**
3. Select branch
4. Click **Run workflow**

### Release Build

#### Option 1: Git Tag (Recommended)

```bash
# Create and push tag
git tag v4.0.6
git push origin v4.0.6

# Or with annotation
git tag -a v4.0.6 -m "Release version 4.0.6"
git push origin v4.0.6
```

#### Option 2: Manual Trigger

1. Go to **Actions** → **Release Build**
2. Click **Run workflow**
3. Enter version (e.g., `4.0.6`)
4. Check "Create GitHub Release"
5. Click **Run workflow**

## 📦 Build Outputs

### Debug Build Artifacts
- Universal APK (all architectures)
- Architecture-specific APKs
- Available for 30 days

### Release Build Artifacts
- Signed APKs (all architectures)
- SHA256 checksums
- Mapping file (if minified)
- GitHub Release (draft)
- Available for 90 days

## 🔍 Verifying Workflows

### Check Workflow Status

1. Go to **Actions** tab
2. Look for running/completed workflows
3. Click on workflow run for details
4. Check logs for any errors

### Download Artifacts

1. Click on workflow run
2. Scroll to **Artifacts** section
3. Click artifact name to download
4. Extract APK from ZIP file

## 🛠️ Troubleshooting

### Workflow Not Triggering

**Check:**
- Workflow file syntax (YAML indentation)
- Branch names match workflow triggers
- Secrets are properly configured
- Submodules initialized: `git submodule update --init --recursive`

### Build Fails

**Common Issues:**
1. **SDK not found**: Workflow handles this automatically
2. **Signing errors**: Check keystore secrets
3. **Out of memory**: Already configured with adequate heap
4. **Submodule errors**: Ensure recursive checkout

### Release Build Without Signing

If no keystore secrets are configured, release builds will:
- Build unsigned APKs
- Create draft release
- Skip signing steps

## 📊 Workflow Configuration

### debug_build.yml

```yaml
Triggers:
  - Push to: master, main, develop
  - Pull requests
  - Manual trigger
  
Outputs:
  - Universal debug APK
  - Architecture-specific APKs
  - GitHub Release (for master/main)
  - Telegram notification
```

### release_build.yml

```yaml
Triggers:
  - Git tags (v*)
  - Manual trigger with version input
  
Outputs:
  - Signed release APKs
  - SHA256 checksums
  - GitHub Release (draft)
  - Mapping files
  - Telegram notification
```

## 🎯 Next Steps

1. **Configure Secrets** (if you want signed releases)
2. **Test Debug Build**:
   - Go to Actions → Debug Build → Run workflow
3. **Monitor Build**:
   - Watch workflow progress in Actions tab
4. **Download APK**:
   - Once complete, download from Artifacts
5. **Test on Device**:
   - Install and verify UX improvements work

## 📝 Commit History

Latest commit: `bbad02fa4`
- Smart Launcher backup import
- Quick filter chips UI
- GitHub Actions CI/CD
- Release signing configuration
- Build documentation

Branch: `refactor/kotlin-conversion`
Tag: `v4.0.6-ux-improvements`

---

**Need Help?**
- Check workflow logs in Actions tab
- Review BUILDING_GUIDE.md for local builds
- Open an issue for build-related problems
