@echo off
title Option App v8 - Auto Starter
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_all.ps1"
