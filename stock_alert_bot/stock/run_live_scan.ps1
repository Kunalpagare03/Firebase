param(
    [int]$IntervalMinutes = 30,
    [switch]$Loop
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv\Scripts\python.exe"
$scanScript = Join-Path $PSScriptRoot "live_stock_scanner.py"
$marketOpen = [DateTime]::Today.AddHours(9).AddMinutes(5)
$marketClose = [DateTime]::Today.AddHours(15).AddMinutes(30)

if (-not (Test-Path $python)) {
    throw "Python environment not found: $python"
}

if (-not (Test-Path $scanScript)) {
    throw "Stock scanner not found: $scanScript"
}

function Invoke-StockScan {
    Write-Host "Running stock live scan at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    & $python $scanScript
    if ($LASTEXITCODE -ne 0) {
        throw "Stock scan exited with code $LASTEXITCODE"
    }
}

if (-not $Loop) {
    Invoke-StockScan
    exit 0
}

while ((Get-Date) -lt $marketClose) {
    if ((Get-Date) -lt $marketOpen) {
        Write-Host "Waiting for market open at $marketOpen"
        Start-Sleep -Seconds 60
        continue
    }

    Invoke-StockScan

    if ((Get-Date) -ge $marketClose) {
        break
    }

    Write-Host "Sleeping for $IntervalMinutes minutes before the next scan..."
    Start-Sleep -Seconds ($IntervalMinutes * 60)
}

Write-Host "Stock live scanner stopped at market close."
