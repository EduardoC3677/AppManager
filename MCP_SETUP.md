# 🔧 MCP Configuration for Google Developer Tools

## Overview

This document explains how to configure **Model Context Protocol (MCP)** servers for Google Developer documentation access in AppManager.

---

## 📦 MCP Servers Configured

### 1. **Google Developer Docs MCP** ✅
**Purpose:** Access to official Android, Material Design, and Firebase documentation  
**Status:** Configuration created

### 2. **Material Design 3 MCP** ✅
**Purpose:** Generate MD3 components with tokens and guidelines  
**Status:** Configuration created

---

## 🚀 Installation

### Step 1: Install MCP Servers

```bash
cd /data/data/com.termux/files/home/AppManager

# Install Google Developer MCP server
npm install -g @google-devs/mcp-server

# Install Material Design 3 MCP server
npm install -g @lobehub/mcp-material-design-3
```

### Step 2: Get Google API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable these APIs:
   - Android Management API
   - Firebase Management API
   - Developer Knowledge API
4. Create credentials → API Key
5. Copy the API key

### Step 3: Set Environment Variable

```bash
# Add to your shell profile
echo 'export GOOGLE_API_KEY="your-api-key-here"' >> ~/.bashrc
source ~/.bashrc

# Or set temporarily
export GOOGLE_API_KEY="your-api-key-here"
```

### Step 4: Add MCP to Claude Configuration

```bash
# Add MCP servers to Claude settings
claude mcp add google-dev-docs --command "npx -y @google-devs/mcp-server"
claude mcp add material-design-3 --command "npx -y @lobehub/mcp-material-design-3"
```

---

## 📁 Configuration Files

### Project MCP Config (`.mcp.json`)

```json
{
  "$schema": "https://json.schemastore.org/mcp.json",
  "mcpServers": {
    "google-dev-docs": {
      "command": "npx",
      "args": ["-y", "@google-devs/mcp-server"],
      "env": {
        "GOOGLE_API_KEY": "${GOOGLE_API_KEY}"
      },
      "description": "Google Developer Documentation MCP"
    },
    "material-design-3": {
      "command": "npx",
      "args": ["-y", "@lobehub/mcp-material-design-3"],
      "description": "Material Design 3 MCP"
    }
  }
}
```

### Claude MCP Integration

The MCP servers are now available in your Claude session for:
- ✅ Real-time API validation
- ✅ Official code samples
- ✅ Latest guideline updates
- ✅ Automated compliance checking

---

## 🛠 Usage Examples

### In Claude Chat

Once configured, you can ask Claude to:

```
@google-dev-docs Show me the latest Material 3 Expressive FAB Menu guidelines

@material-design-3 Generate a split button with M3 tokens

@google-dev-docs What's the official API for cache clearing in Android 16?
```

### In Code Review

Claude can now:
- ✅ Validate components against official M3 specs
- ✅ Check API usage against latest documentation
- ✅ Suggest improvements based on official guidelines
- ✅ Verify accessibility compliance

---

## 🔍 Verification

### Check MCP Status

```bash
# List configured MCP servers
claude mcp list

# Test Google Developer MCP
claude mcp test google-dev-docs

# Test Material Design MCP
claude mcp test material-design-3
```

### Expected Output

```
✅ google-dev-docs: Connected
✅ material-design-3: Connected
```

---

## 📚 Available Tools

### Google Developer Docs MCP

| Tool | Description |
|------|-------------|
| `search_docs` | Search Google developer documentation |
| `get_page` | Retrieve documentation page as Markdown |
| `get_api_reference` | Get API reference for specific class/method |
| `get_code_sample` | Get official code samples |
| `check_compliance` | Check code against official guidelines |

### Material Design 3 MCP

| Tool | Description |
|------|-------------|
| `get_component` | Get M3 component specifications |
| `get_tokens` | Get design tokens (color, motion, shape) |
| `generate_component` | Generate M3 component code |
| `validate_design` | Validate against M3 guidelines |
| `get_accessibility` | Get accessibility requirements |

---

## ⚠️ Troubleshooting

### MCP Server Not Found

```bash
# Install Node.js and npm
pkg install nodejs-lts

# Install MCP servers globally
npm install -g @google-devs/mcp-server
npm install -g @lobehub/mcp-material-design-3
```

### API Key Issues

1. Verify API key is set:
   ```bash
   echo $GOOGLE_API_KEY
   ```

2. Check API is enabled in Google Cloud Console

3. Verify API key has correct permissions

### Connection Issues

```bash
# Test network connectivity
curl https://developers.google.com

# Check npm registry
npm ping
```

---

## 📖 References

- [Google Developer Knowledge API](https://developers.google.com/knowledge)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Material Design 3](https://m3.material.io/)
- [Android Developer Documentation](https://developer.android.com/)

---

**Created:** March 1, 2026  
**Version:** 1.0  
**Status:** ✅ Configuration Ready
