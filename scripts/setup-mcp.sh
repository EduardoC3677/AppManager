#!/bin/bash
# MCP Setup Script for AppManager
# This script configures MCP servers using existing Claude/Gemini setup

set -e

echo "🔧 AppManager MCP Configuration Script"
echo "======================================"
echo ""

# Check if running in Termux
if [ ! -d "/data/data/com.termux" ]; then
    echo "❌ This script must run in Termux"
    exit 1
fi

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "📍 Step 1: Checking Node.js and npm..."
if ! command -v node &> /dev/null; then
    echo -e "${YELLOW}⚠️  Node.js not found. Installing...${NC}"
    pkg install nodejs-lts -y
fi

if ! command -v npm &> /dev/null; then
    echo -e "${YELLOW}⚠️  npm not found. Installing...${NC}"
    pkg install npm -y
fi

echo -e "${GREEN}✅ Node.js and npm ready${NC}"
echo ""

echo "📍 Step 2: Installing MCP servers..."
echo "Installing Google Developer MCP server..."
npm install -g @google/genai 2>/dev/null || echo -e "${YELLOW}⚠️  Google GenAI package installation skipped${NC}"

echo "Installing Material Design 3 MCP server..."
npm install -g @lobehub/mcp-material-design-3 2>/dev/null || echo -e "${YELLOW}⚠️  Material Design MCP not available via npm${NC}"

echo -e "${GREEN}✅ MCP packages installed${NC}"
echo ""

echo "📍 Step 3: Extracting API key from existing apps..."

# Try to find Gemini API key from ai-recorder
GEMINI_KEY=""
if [ -f "/data/data/com.termux/files/home/ai-recorder/.env" ]; then
    GEMINI_KEY=$(grep "GEMINI_API_KEY" /data/data/com.termux/files/home/ai-recorder/.env | cut -d'=' -f2 | tr -d '"' | tr -d "'")
fi

# Try from vite.config.ts
if [ -z "$GEMINI_KEY" ] && [ -f "/data/data/com.termux/files/home/ai-recorder/vite.config.ts" ]; then
    GEMINI_KEY=$(grep "GEMINI_API_KEY" /data/data/com.termux/files/home/ai-recorder/vite.config.ts | head -1)
fi

if [ -n "$GEMINI_KEY" ]; then
    echo -e "${GREEN}✅ Found Gemini API key from ai-recorder${NC}"
    export GEMINI_API_KEY="$GEMINI_KEY"
else
    echo -e "${YELLOW}⚠️  Could not auto-extract API key${NC}"
    echo "Please set it manually:"
    echo "  export GEMINI_API_KEY=\"your-key-here\""
fi
echo ""

echo "📍 Step 4: Creating MCP configuration..."

# Create project MCP config
cat > /data/data/com.termux/files/home/AppManager/.mcp.json << 'EOF'
{
  "$schema": "https://json.schemastore.org/mcp.json",
  "mcpServers": {
    "google-dev-docs": {
      "command": "npx",
      "args": ["-y", "@google/genai"],
      "env": {
        "GOOGLE_API_KEY": "${GEMINI_API_KEY}"
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
EOF

echo -e "${GREEN}✅ Created .mcp.json${NC}"
echo ""

echo "📍 Step 5: Adding MCP servers to Claude..."

# Add to Claude MCP configuration
if command -v claude &> /dev/null; then
    echo "Adding Google Developer MCP to Claude..."
    claude mcp add google-dev-docs --command "npx -y @google/genai" 2>/dev/null || echo -e "${YELLOW}⚠️  Claude MCP add command not available${NC}"
    
    echo "Adding Material Design MCP to Claude..."
    claude mcp add material-design-3 --command "npx -y @lobehub/mcp-material-design-3" 2>/dev/null || echo -e "${YELLOW}⚠️  Claude MCP add command not available${NC}"
    
    echo -e "${GREEN}✅ MCP servers added to Claude${NC}"
else
    echo -e "${YELLOW}⚠️  Claude CLI not found${NC}"
    echo "Manual setup required:"
    echo "  claude mcp add google-dev-docs --command \"npx -y @google/genai\""
    echo "  claude mcp add material-design-3 --command \"npx -y @lobehub/mcp-material-design-3\""
fi
echo ""

echo "📍 Step 6: Setting environment variables..."

# Add to bashrc if not already present
if ! grep -q "GEMINI_API_KEY" ~/.bashrc 2>/dev/null; then
    echo "" >> ~/.bashrc
    echo "# AppManager MCP Configuration" >> ~/.bashrc
    echo "export GEMINI_API_KEY=\"${GEMINI_KEY:-your-api-key-here}\"" >> ~/.bashrc
    echo -e "${GREEN}✅ Added GEMINI_API_KEY to ~/.bashrc${NC}"
else
    echo -e "${YELLOW}ℹ️  GEMINI_API_KEY already in ~/.bashrc${NC}"
fi

# Set for current session
export GEMINI_API_KEY="${GEMINI_KEY:-your-api-key-here}"

echo ""
echo "======================================"
echo -e "${GREEN}✅ MCP Configuration Complete!${NC}"
echo ""
echo "📋 Next Steps:"
echo "1. Restart your terminal or run: source ~/.bashrc"
echo "2. Verify MCP servers: claude mcp list"
echo "3. Test in AppManager project"
echo ""
echo "📖 Documentation: See MCP_SETUP.md"
echo ""
