# ==============================================================================
# Option App v8 - Remove from Windows Auto-Start
# ==============================================================================

$StartupFolder = [Environment]::GetFolderPath("Startup")
$ShortcutPath = Join-Path $StartupFolder "OptionApp_v8_AutoStart.lnk"

if (Test-Path $ShortcutPath) {
    Remove-Item $ShortcutPath -Force
    Write-Host "✅ Removed Option App v8 from Windows Startup." -ForegroundColor Green
} else {
    Write-Host "Option App v8 was not found in Windows Startup folder." -ForegroundColor Yellow
}
