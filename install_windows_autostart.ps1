# ==============================================================================
# Option App v8 - Add to Windows Auto-Start on Boot / Login
# ==============================================================================

$WorkspaceRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$TargetBat = Join-Path $WorkspaceRoot "start_all.bat"
$StartupFolder = [Environment]::GetFolderPath("Startup")
$ShortcutPath = Join-Path $StartupFolder "OptionApp_v8_AutoStart.lnk"

$WshShell = New-Object -ComObject WScript.Shell
$Shortcut = $WshShell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $TargetBat
$Shortcut.WorkingDirectory = $WorkspaceRoot
$Shortcut.Description = "Auto-Start Option App v8 on Windows Login"
$Shortcut.Save()

Write-Host "✅ Option App v8 successfully registered to Windows Startup!" -ForegroundColor Green
Write-Host "Location: $ShortcutPath" -ForegroundColor White
Write-Host "The app will now automatically start every time you log in to Windows." -ForegroundColor Cyan
