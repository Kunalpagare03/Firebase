$ErrorActionPreference = "Stop"

$timeZone = Get-TimeZone
if ($timeZone.Id -ne "India Standard Time") {
    throw "Set the Windows time zone to Asia/Kolkata before registering this 23:00 daily task."
}

$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    throw "Create the local Python environment and install requirements.txt first."
}

$credentialPath = [Environment]::GetEnvironmentVariable("GOOGLE_APPLICATION_CREDENTIALS", "User")
$databaseUrl = [Environment]::GetEnvironmentVariable("OPTION_APP_DATABASE_URL", "User")
$passwordFile = [Environment]::GetEnvironmentVariable("PGPASSFILE", "User")
if (-not $credentialPath -or -not $databaseUrl -or -not $passwordFile) {
    throw "Set GOOGLE_APPLICATION_CREDENTIALS, OPTION_APP_DATABASE_URL, and PGPASSFILE as user environment variables first."
}
if (-not (Test-Path $credentialPath) -or -not (Test-Path $passwordFile)) {
    throw "The configured Firebase credential or PostgreSQL password file does not exist."
}

$powerShell = Join-Path $PSHOME "powershell.exe"
$runner = Join-Path $PSScriptRoot "run_daily_sync.ps1"
$action = New-ScheduledTaskAction `
    -Execute $powerShell `
    -Argument ('-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $runner) `
    -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -Daily -At "11:00PM"
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2) `
    -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal `
    -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive `
    -RunLevel Limited

Register-ScheduledTask `
    -TaskName "KunalOptionPostgresDailySync" `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Description "Daily 23:00 Asia/Kolkata Option app Firestore snapshot to the local option_app database." `
    -Force | Out-Null

Write-Output "Registered Option app PostgreSQL snapshot for 23:00 Asia/Kolkata."
