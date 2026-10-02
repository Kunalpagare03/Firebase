$ErrorActionPreference = "Stop"
$startupDirectory = [Environment]::GetFolderPath("Startup")
$target = Join-Path $PSScriptRoot "start_market_services_at_signin.ps1"
if (-not (Test-Path -LiteralPath $target)) {
    throw "Startup launcher was not found: $target"
}
if (-not (Test-Path -LiteralPath $startupDirectory)) {
    throw "Windows Startup folder was not found: $startupDirectory"
}

$shortcutPath = Join-Path $startupDirectory "StockPulse Market Services.lnk"
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = Join-Path $env:WINDIR "System32\WindowsPowerShell\v1.0\powershell.exe"
$shortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$target`""
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.WindowStyle = 7
$shortcut.Description = "Start the StockPulse option feed, stock scanner and mobile command listener at sign-in."
$shortcut.Save()
Write-Host "Installed StockPulse startup shortcut: $shortcutPath"
