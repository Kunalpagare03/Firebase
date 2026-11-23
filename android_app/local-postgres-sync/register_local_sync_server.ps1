$ErrorActionPreference = "Stop"

$timeZone = Get-TimeZone
if ($timeZone.Id -ne "India Standard Time") {
    throw "Set the Windows time zone to Asia/Kolkata before registering the local sync server."
}

$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
$runner = Join-Path $PSScriptRoot "run_local_sync_server.ps1"
if (-not (Test-Path $python)) {
    throw "Create the local Python environment and install requirements.txt first."
}

$requiredVariables = @(
    "GOOGLE_APPLICATION_CREDENTIALS",
    "OPTION_APP_DATABASE_URL",
    "PGPASSFILE"
)
foreach ($name in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($name, "User")
    if (-not $value) {
        throw "Set $name as a user environment variable before registering the local sync server."
    }
}

$powerShell = Join-Path $PSHOME "powershell.exe"
$action = New-ScheduledTaskAction `
    -Execute $powerShell `
    -Argument ('-WindowStyle Hidden -NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $runner) `
    -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger `
    -AtLogOn `
    -User ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -RandomDelay (New-TimeSpan -Seconds 30)
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Seconds 0) `
    -RestartCount 10 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal `
    -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive `
    -RunLevel Limited

Register-ScheduledTask `
    -TaskName "KunalOptionLocalSyncServer" `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Description "Starts the Option app authenticated HTTPS PostgreSQL API on trusted Wi-Fi at user logon." `
    -Force | Out-Null

Write-Output "Registered Option local sync API for user logon."
