$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$startupPrincipal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
$liveScript = Join-Path $root "stock\run_live_scan.ps1"
$afterMarketScript = Join-Path $root "stock\run_after_market.ps1"

$liveAction = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$liveScript`" -Loop -IntervalMinutes 30"
$marketTrigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday -At 09:05
$logonTrigger = New-ScheduledTaskTrigger -AtLogOn
$liveSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 7) -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName "Stock Alert Bot - Intraday Live Scan" -Action $liveAction -Trigger @($marketTrigger, $logonTrigger) -Settings $liveSettings -Description "Start the stock scanner at sign-in or 9:05 AM on weekdays and scan every 30 minutes through market close." -Force | Out-Null

# Keep the original market-open task for machines already running in the morning;
# this separate per-user task starts the scanner when the laptop is signed in.
$startupTrigger = New-ScheduledTaskTrigger -AtLogOn
Register-ScheduledTask -TaskName "Stock Alert Bot - Sign-In Live Scan" -Action $liveAction -Trigger $startupTrigger -Settings $liveSettings -Principal $startupPrincipal -Description "Start the stock scanner when the user signs in; it waits for market open if needed." -Force | Out-Null

$afterMarketAction = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$afterMarketScript`""
$afterMarketTrigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday -At 15:40
$afterMarketSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 2) -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName "Stock Alert Bot - After Market Scan" -Action $afterMarketAction -Trigger $afterMarketTrigger -Settings $afterMarketSettings -Description "Run Chartink stock analysis after market close." -Force | Out-Null

Write-Host "Registered stock tasks: intraday scan at sign-in or 9:05 with 30-minute repeats, and after-market scan at 15:40."
