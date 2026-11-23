$ErrorActionPreference = "Stop"

$env:GOOGLE_APPLICATION_CREDENTIALS = [Environment]::GetEnvironmentVariable(
    "GOOGLE_APPLICATION_CREDENTIALS",
    "User"
)
$env:STOCK_APP_DATABASE_URL = [Environment]::GetEnvironmentVariable(
    "STOCK_APP_DATABASE_URL",
    "User"
)
$env:PGPASSFILE = [Environment]::GetEnvironmentVariable("PGPASSFILE", "User")

if (-not $env:GOOGLE_APPLICATION_CREDENTIALS -or
    -not $env:STOCK_APP_DATABASE_URL -or
    -not $env:PGPASSFILE) {
    throw "Required Stock app sync environment variables are not configured."
}

$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
$syncScript = Join-Path $PSScriptRoot "sync_firestore_to_postgres.py"
$logDirectory = Join-Path $env:LOCALAPPDATA "PostgreSQL\17\sync-logs"
$logFile = Join-Path $logDirectory ("stock-sync-{0}.log" -f (Get-Date -Format "yyyy-MM-dd"))
$errorFile = Join-Path $logDirectory ("stock-sync-{0}-errors.log" -f (Get-Date -Format "yyyy-MM-dd"))
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null

$process = Start-Process `
    -FilePath $python `
    -ArgumentList ('"{0}"' -f $syncScript) `
    -WorkingDirectory $PSScriptRoot `
    -NoNewWindow `
    -Wait `
    -PassThru `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errorFile
if ($process.ExitCode -ne 0) {
    throw "Stock app daily snapshot failed with exit code $($process.ExitCode). See $errorFile."
}
