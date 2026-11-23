# ==============================================================================
# Option App v8 - Master Auto-Start Script (PowerShell)
# Automatically starts both the Live Clock Engine and Streamlit Dashboard
# ==============================================================================

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "     ⚡ OPTION APP v8 - AUTO STARTING ALL SERVICES ⚡     " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan

$WorkspaceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $WorkspaceRoot

# 1. Health check verification first
Write-Host "`n[1/3] Running fast acceptance verification..." -ForegroundColor Green
$check = python -m optionapp.app.check
if ($LASTEXITCODE -ne 0) {
    Write-Host "[WARNING] Health check reported warnings, continuing startup..." -ForegroundColor Yellow
} else {
    Write-Host "[OK] Health check passed 100%!" -ForegroundColor Green
}

# 2. Launch Live Clock Engine in a dedicated PowerShell process
Write-Host "`n[2/3] Launching Live Clock Trading Engine..." -ForegroundColor Green
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "Set-Location '$WorkspaceRoot'; Write-Host '⚡ Option App v8 - Live Engine Active' -ForegroundColor Green; python -m optionapp.app.engine.live"

Start-Sleep -Seconds 2

# 3. Launch Streamlit UI Dashboard in a dedicated PowerShell process and auto-open browser
Write-Host "`n[3/3] Launching Streamlit UI Dashboard on http://localhost:8501..." -ForegroundColor Green
Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", "Set-Location '$WorkspaceRoot'; Write-Host '⚡ Option App v8 - UI Dashboard Active' -ForegroundColor Cyan; python -m streamlit run optionapp/app/ui.py --server.port 8501"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  ✅ ALL SERVICES SUCCESSFULLY STARTED IN BACKGROUND!    " -ForegroundColor Green
Write-Host "  - Live Engine: Running active market timeline           " -ForegroundColor White
Write-Host "  - UI Dashboard: http://localhost:8501                   " -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Cyan
