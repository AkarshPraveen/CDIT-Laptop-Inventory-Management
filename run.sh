#!/bin/bash
# Automatically locate the directory where run.sh lives
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Launch using local portable JRE and local libraries
"$SCRIPT_DIR/jre/bin/java" -cp "$SCRIPT_DIR:$SCRIPT_DIR/libs/*" LaptopScannerGUI
