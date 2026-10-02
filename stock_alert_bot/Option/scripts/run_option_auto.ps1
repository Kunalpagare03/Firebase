param(
    [string]$Symbol = "NIFTY",
    [int]$IntervalMinutes = 5,
    [switch]$Loop
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$python = Join-Path $workspaceRoot ".venv\Scripts\python.exe"
$marketOpen = [DateTime]::Today.AddHours(9).AddMinutes(5)
$marketClose = [DateTime]::Today.AddHours(15).AddMinutes(30)

if (-not (Test-Path $python)) {
    throw "Python environment not found: $python"
}

function Invoke-OptionRun {
    Write-Host "Running option scan at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    $env:SYMBOL = $Symbol
    & $python "scripts\cloud_market_runner.py"
    if ($LASTEXITCODE -ne 0) {
        throw "Option runner exited with code $LASTEXITCODE"
    }
}

if (-not $Loop) {
    Invoke-OptionRun
    exit 0
}

while ((Get-Date) -lt $marketClose) {
    if ((Get-Date) -lt $marketOpen) {
        Write-Host "Waiting for market open at $marketOpen"
        Start-Sleep -Seconds 60
        continue
    }

    Invoke-OptionRun
    if ((Get-Date) -ge $marketClose) {
        break
    }

    Write-Host "Sleeping for $IntervalMinutes minutes before the next scan..."
    Start-Sleep -Seconds ($IntervalMinutes * 60)
}

Write-Host "Option auto runner stopped at market close."
