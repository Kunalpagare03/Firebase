param(
    [int]$IntervalMinutes = 30,
    [switch]$Loop
)

$ErrorActionPreference = "Stop"
$scanMutex = [System.Threading.Mutex]::new($false, "Local\StockAlertBot.LiveScan")
if (-not $scanMutex.WaitOne(0)) {
    Write-Host "A stock scan session is already active; ignoring this duplicate launch."
    exit 0
}

try {
$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root ".venv\Scripts\python.exe"
$scanScript = Join-Path $PSScriptRoot "live_stock_scanner.py"
$logDir = Join-Path $PSScriptRoot "logs\scheduled"
$logFile = Join-Path $logDir ("live_scan_{0}.log" -f (Get-Date -Format "yyyy-MM-dd"))
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (-not (Test-Path $python)) {
    throw "Python environment not found: $python"
}

if (-not (Test-Path $scanScript)) {
    throw "Stock scanner not found: $scanScript"
}

function Invoke-StockScan {
    Write-Host "Running stock live scan at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    # Unbuffered output keeps scan progress visible while the 2,800+ symbol
    # universe is still running instead of leaving the local log blank.
    # Windows PowerShell can turn ordinary stderr warnings from Python/yfinance
    # into terminating errors when ErrorActionPreference is Stop. Capture them
    # as log output and rely on the native exit code to detect a real failure.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & $python -u $scanScript 2>&1 | Tee-Object -FilePath $logFile -Append
        $scanExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($scanExitCode -ne 0) {
        throw "Stock scan exited with code $scanExitCode"
    }
}

if (-not $Loop) {
    Invoke-StockScan
    return
}

while ($true) {
    $now = Get-Date
    $marketOpen = $now.Date.AddHours(9).AddMinutes(5)
    $marketClose = $now.Date.AddHours(15).AddMinutes(30)
    if ($now -lt $marketOpen) {
        Write-Host "Waiting for market open at $marketOpen"
        Start-Sleep -Seconds 60
        continue
    }
    if ($now -ge $marketClose) {
        Write-Host "Market closed for today; waiting for the next session."
        Start-Sleep -Seconds 60
        continue
    }

    Invoke-StockScan

    if ((Get-Date) -lt $marketClose) {
        Write-Host "Sleeping for $IntervalMinutes minutes before the next scan..."
        Start-Sleep -Seconds ($IntervalMinutes * 60)
    }
}
}
finally {
    $scanMutex.ReleaseMutex()
    $scanMutex.Dispose()
}
