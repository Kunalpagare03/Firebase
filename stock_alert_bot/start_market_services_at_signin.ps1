$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$optionRoot = Join-Path $root "Option"
$logDir = Join-Path $optionRoot "logs\scheduled\sign-in"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$optionSession = Join-Path $optionRoot "scripts\run_market_session.ps1"
$stockSession = Join-Path $root "stock\run_live_scan.ps1"

foreach ($name in @("ANGEL_API_KEY", "ANGEL_CLIENT_CODE", "ANGEL_PIN", "ANGEL_TOTP_SECRET")) {
    $value = [Environment]::GetEnvironmentVariable($name, "User")
    if ($value) { Set-Item -Path "Env:$name" -Value $value }
}

Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$optionSession`"") `
    -WorkingDirectory $optionRoot -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDir "option.out.log") `
    -RedirectStandardError (Join-Path $logDir "option.err.log")

Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$stockSession`"", "-Loop", "-IntervalMinutes", "30") `
    -WorkingDirectory $root -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDir "stock.out.log") `
    -RedirectStandardError (Join-Path $logDir "stock.err.log")

