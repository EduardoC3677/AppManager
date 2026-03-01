#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
# GitHub Secrets Setup Script for AppManager
# Run this after installing GitHub CLI: gh auth login

set -e

REPO="thejaustin/AppManager"

echo "🔐 AppManager GitHub Secrets Setup"
echo "=================================="
echo ""

# Check if gh is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed."
    echo "Install it first: https://cli.github.com/"
    exit 1
fi

# Check authentication
if ! gh auth status &> /dev/null; then
    echo "❌ Not authenticated with GitHub CLI."
    echo "Run: gh auth login"
    exit 1
fi

echo "✅ Authenticated with GitHub CLI"
echo ""

# Function to set secret
set_secret() {
    local secret_name=$1
    local secret_value=$2
    
    if [ -z "$secret_value" ]; then
        echo "⚠️  Skipping $secret_name (no value provided)"
        return
    fi
    
    echo "🔑 Setting $secret_name..."
    gh secret set "$secret_name" --body "$secret_value" --repo "$REPO" 2>/dev/null && \
        echo "✅ $secret_name set successfully" || \
        echo "❌ Failed to set $secret_name"
}

echo "📝 Enter your secrets (press Enter to skip):"
echo ""

# Keystore secrets
echo "--- Release Signing Secrets ---"
read -p "RELEASE_KEYSTORE_B64 (base64 encoded keystore): " KEYSTORE_B64
read -p "KEYSTORE_STORE_PASSWORD: " STORE_PASS
read -p "KEYSTORE_KEY_PASSWORD: " KEY_PASS
read -p "KEYSTORE_KEY_ALIAS: " KEY_ALIAS

set_secret "RELEASE_KEYSTORE_B64" "$KEYSTORE_B64"
set_secret "KEYSTORE_STORE_PASSWORD" "$STORE_PASS"
set_secret "KEYSTORE_KEY_PASSWORD" "$KEY_PASS"
set_secret "KEYSTORE_KEY_ALIAS" "$KEY_ALIAS"

echo ""
echo "--- Telegram Notification Secrets (Optional) ---"
read -p "TELEGRAM_TOKEN (Bot token from @BotFather): " TG_TOKEN
read -p "TELEGRAM_TO (Channel/Chat ID): " TG_TO

set_secret "TELEGRAM_TOKEN" "$TG_TOKEN"
set_secret "TELEGRAM_TO" "$TG_TO"

echo ""
echo "--- F-Droid Repo Secret (Optional) ---"
read -p "FDROID_REPO_TOKEN (GitHub token): " FDROID_TOKEN

set_secret "FDROID_REPO_TOKEN" "$FDROID_TOKEN"

echo ""
echo "=================================="
echo "✅ Secrets setup complete!"
echo ""
echo "📋 To verify secrets, go to:"
echo "https://github.com/$REPO/settings/secrets/actions"
echo ""
echo "🚀 To trigger a release build:"
echo "git tag v4.0.6"
echo "git push origin v4.0.6"
echo ""
