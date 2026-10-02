$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$startupPrincipal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType Interactive -RunLevel Limited
$sessionScript = Join-Path $root "scripts\run_market_session.ps1"
$listenerScript = Join-Path $root "scripts\run_command_listener.ps1"
$taskName = "Stock Alert Bot - Market Session"
$action = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$sessionScript`""
$marketTrigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday,Tuesday,Wednesday,Thursday,Friday -At 09:15
$logonTrigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 7) -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger @($marketTrigger, $logonTrigger) -Settings $settings -Description "Start option analysis at sign-in or 09:15 on weekdays and stop after market close." -Force | Out-Null
Write-Host "Registered: $taskName, at sign-in and Monday-Friday at 09:15. Option analysis refreshes every 5 minutes during market hours."

# The existing market-open task may be protected by Windows. Use a separate
# current-user task so signing in can still start the live service.
$startupTaskName = "Stock Alert Bot - Sign-In Market Session"
$startupTrigger = New-ScheduledTaskTrigger -AtLogOn
$startupSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 7) -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
Register-ScheduledTask -TaskName $startupTaskName -Action $action -Trigger $startupTrigger -Settings $startupSettings -Principal $startupPrincipal -Description "Start option analysis at user sign-in and stop after market close." -Force | Out-Null
Write-Host "Registered: $startupTaskName, at sign-in."
Write-Host "Please ensure you have configured your credentials (ANGEL_API_KEY, etc.) in your User Environment Variables."

$listenerTaskName = "Stock Alert Bot - Sign-In Mobile Commands"
$listenerAction = New-ScheduledTaskAction -Execute "PowerShell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$listenerScript`""
$listenerTrigger = New-ScheduledTaskTrigger -AtLogOn
$listenerSettings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Days 365) -StartWhenAvailable
Register-ScheduledTask -TaskName $listenerTaskName -Action $listenerAction -Trigger $listenerTrigger -Settings $listenerSettings -Principal $startupPrincipal -Description "Receive mobile Stock Scan and Option refresh commands while this laptop is on." -Force | Out-Null
Write-Host "Registered: $listenerTaskName, at sign-in for manual mobile commands."
