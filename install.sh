#!/bin/bash
# Get absolute path to the current directory on whatever laptop this script is executed on
APP_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
USER_DESKTOP="$(eval echo ~$USER)/Desktop"
APPLICATIONS_DIR="$(eval echo ~$USER)/.local/share/applications"

echo "Installing C-DIT CBT Inventory Agent on this system..."

# Create desktop entry content dynamically with correct local user paths
DESKTOP_FILE_CONTENT="[Desktop Entry]
Version=1.0
Type=Application
Name=C-DIT CBT Inventory Agent
Comment=Register CBT Exam Server Hardware to CMS
Exec=$APP_DIR/run.sh
Icon=$APP_DIR/app-icon.png
Terminal=false
Categories=Utility;System;
StartupNotify=true"

# Write shortcut to the Desktop
echo "$DESKTOP_FILE_CONTENT" > "$USER_DESKTOP/C-DIT-CBT-Agent.desktop"
chmod +x "$USER_DESKTOP/C-DIT-CBT-Agent.desktop"

# Write shortcut to system Application Menu (Show Applications search)
mkdir -p "$APPLICATIONS_DIR"
echo "$DESKTOP_FILE_CONTENT" > "$APPLICATIONS_DIR/cdit-cbt-agent.desktop"
chmod +x "$APPLICATIONS_DIR/cdit-cbt-agent.desktop"

# Trust the desktop shortcut (Ubuntu specific)
gio trust "$USER_DESKTOP/C-DIT-CBT-Agent.desktop" 2>/dev/null || true

echo "--------------------------------------------------------"
echo "✅ Installation Complete!"
echo "The app icon is now available on the Desktop and in the System Applications Menu."
echo "--------------------------------------------------------"
