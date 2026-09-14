$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$liveScript = Join-Path $root "stock\run_live_scan.ps1"
$afterMarketScript = Join-Path $root "stock\run_after_market.ps1"

$liveAction = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$liveScript`" -Loop -IntervalMinutes 30"
$liveTrigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday -At 09:05
$liveSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 2) -StartWhenAvailable
Register-ScheduledTask -TaskName "Stock Alert Bot - Intraday Live Scan" -Action $liveAction -Trigger $liveTrigger -Settings $liveSettings -Description "Run the stock live scanner from 9:05 AM to 3:30 PM on weekdays, scanning every 30 minutes." -Force | Out-Null

$afterMarketAction = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$afterMarketScript`""
$afterMarketTrigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday -At 15:40
$afterMarketSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 2) -StartWhenAvailable
Register-ScheduledTask -TaskName "Stock Alert Bot - After Market Scan" -Action $afterMarketAction -Trigger $afterMarketTrigger -Settings $afterMarketSettings -Description "Run Chartink stock analysis after market close." -Force | Out-Null

Write-Host "Registered stock tasks: intraday live scan at 9:05 with 30-minute repeats, and after-market scan at 15:40."