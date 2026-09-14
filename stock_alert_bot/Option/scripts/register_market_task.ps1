$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sessionScript = Join-Path $root "scripts\run_market_session.ps1"
$taskName = "Stock Alert Bot - Market Session"
$action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$sessionScript`""
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday -At 09:05
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 7) -StartWhenAvailable
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description "Run the option dashboard and Cloudflare tunnel from 9:05 AM to market close on weekdays." -Force | Out-Null
Write-Host "Registered: $taskName, Monday-Friday at 09:05."
Write-Host "Please ensure you have configured your credentials (ANGEL_API_KEY, etc.) in your User Environment Variables."