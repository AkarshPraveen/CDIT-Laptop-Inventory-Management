@echo off
cd /d "%~dp0"
title C-DIT CBT Server Inventory Agent
".\jre\bin\java.exe" -cp ".;libs/*" LaptopScannerGUI
pause