@echo off
title Stock App (v4) - 1-Click Launch All
echo ===================================================
echo  STARTING STOCK APP (v4) - ALL SERVICES
echo ===================================================
call python -m stockapp.app.check
if %errorlevel% neq 0 (
    echo [ERROR] System health check failed!
    pause
    exit /b %errorlevel%
)
echo [1/2] Running Nightly Quant Scanner...
call python -m stockapp.app.nightly --offline
echo [2/2] Launching Interactive Streamlit Dashboard...
start "" streamlit run stockapp/app/ui.py --server.port 8502
echo ===================================================
echo  STOCK APP ACTIVE AT http://localhost:8502
echo ===================================================
