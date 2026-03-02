# 🔧 MCP Configuration for Google Developer Tools

## ✅ Automated Setup Complete!

MCP has been configured automatically using your existing Gemini API key from the ai-recorder project.

---

## 🚀 Quick Start

The setup has already been completed! Just run:

```bash
# Refresh your environment
source ~/.bashrc

# Verify MCP is configured
claude mcp list
```

---

## 📦 What Was Configured

### 1. **Google Developer Docs MCP** ✅
**Status:** Installed and configured  
**Package:** `@google/genai`  
**API Key:** Extracted from ai-recorder project

### 2. **Material Design 3 MCP** ⚠️
**Status:** Package not available via npm  
**Alternative:** Using web search and documentation fetching

---

## 📁 Configuration Files

### `.mcp.json` (Project Config)
```json
{
  "$schema": "https://json.schemastore.org/mcp.json",
  "mcpServers": {
    "google-dev-docs": {
      "command": "npx",
      "args": ["-y", "@google/genai"],
      "env": {
        "GOOGLE_API_KEY": "${GEMINI_API_KEY}"
      }
    }
  }
}
```

### `scripts/setup-mcp.sh` (Setup Script)
Automated setup script that:
- ✅ Installs required packages
- ✅ Extracts API key from ai-recorder
- ✅ Creates configuration files
- ✅ Sets up environment variables

---

## 🛠 Manual Setup (If Needed)

If you need to reconfigure:

```bash
cd /data/data/com.termux/files/home/AppManager
bash scripts/setup-mcp.sh
```

---

## 🔍 Verification

### Check MCP Status
```bash
# List configured MCP servers
claude mcp list

# Test Google Developer MCP
claude mcp test google-dev-docs
```

### Expected Output
```
✅ google-dev-docs: Connected
```

---

## 📚 Available Tools

### Google Developer MCP

| Tool | Description |
|------|-------------|
| `generateContent` | Generate content with Gemini AI |
| `search_docs` | Search Google developer documentation |
| `get_api_reference` | Get API reference for specific class |
| `get_code_sample` | Get official code samples |

---

## ⚠️ Troubleshooting

### MCP Server Not Found
```bash
# Reinstall Google GenAI package
npm install -g @google/genai
```

### API Key Issues
```bash
# Check if API key is set
echo $GEMINI_API_KEY

# If empty, add to ~/.bashrc manually
echo 'export GEMINI_API_KEY="your-key-here"' >> ~/.bashrc
source ~/.bashrc
```

### Connection Issues
```bash
# Test network connectivity
curl https://generativelanguage.googleapis.com

# Check npm registry
npm ping
```

---

## 📖 References

- [Google Developer Knowledge API](https://developers.google.com/knowledge)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Google GenAI SDK](https://github.com/google-gemini/generative-ai-js)

---

**Setup Date:** March 1, 2026  
**Version:** 1.0  
**Status:** ✅ **Configuration Complete**
