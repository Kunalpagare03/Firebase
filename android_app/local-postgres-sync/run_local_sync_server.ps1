$ErrorActionPreference = "Stop"

$env:GOOGLE_APPLICATION_CREDENTIALS = [Environment]::GetEnvironmentVariable(
    "GOOGLE_APPLICATION_CREDENTIALS",
    "User"
)
$env:OPTION_APP_DATABASE_URL = [Environment]::GetEnvironmentVariable(
    "OPTION_APP_DATABASE_URL",
    "User"
)
$env:PGPASSFILE = [Environment]::GetEnvironmentVariable("PGPASSFILE", "User")

if (-not $env:GOOGLE_APPLICATION_CREDENTIALS -or
    -not $env:OPTION_APP_DATABASE_URL -or
    -not $env:PGPASSFILE) {
    throw "Required Option local sync server environment variables are not configured."
}

$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
$server = Join-Path $PSScriptRoot "local_sync_server.py"
$logDirectory = Join-Path $env:LOCALAPPDATA "PostgreSQL\17\api\option"
$logFile = Join-Path $logDirectory "server.log"
$errorFile = Join-Path $logDirectory "server-errors.log"
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$process = Start-Process `
    -FilePath $python `
    -ArgumentList ('"{0}"' -f $server) `
    -WorkingDirectory $PSScriptRoot `
    -NoNewWindow `
    -Wait `
    -PassThru `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errorFile
if ($process.ExitCode -ne 0) {
    throw "Option local sync server exited with code $($process.ExitCode). See $errorFile."
}
