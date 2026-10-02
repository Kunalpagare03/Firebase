$ErrorActionPreference = "Stop"
$sessionMutex = [System.Threading.Mutex]::new($false, "Local\StockAlertBot.MarketSession")
if (-not $sessionMutex.WaitOne(0)) {
    Write-Host "A market-session supervisor is already active; ignoring this duplicate launch."
    exit 0
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$python = Join-Path $workspaceRoot ".venv\Scripts\python.exe"
$logDir = Join-Path $projectRoot "logs\scheduled"
New-Item -ItemType Directory -Force $logDir | Out-Null
$supervisorLog = Join-Path $logDir "session_supervisor.log"

if (-not (Test-Path $python)) {
    throw "Python environment not found: $python"
}
$credentialNames = @('ANGEL_API_KEY', 'ANGEL_CLIENT_CODE', 'ANGEL_PIN', 'ANGEL_TOTP_SECRET')
foreach ($name in $credentialNames) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "Env:$name" -Value $value }
}

$stockRoot = Join-Path $workspaceRoot "stock"

function Find-RunningServiceProcess([string]$scriptPattern) {
    $process = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -eq $python -and $_.CommandLine -match $scriptPattern } |
        Select-Object -First 1
    if ($process) {
        try { return Get-Process -Id $process.ProcessId -ErrorAction Stop } catch { }
    }
    return $null
}

function Start-AnalysisProcess {
    $existing = Find-RunningServiceProcess "scripts\\cloud_market_runner\.py"
    if ($existing) {
        Add-Content -Path $supervisorLog -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') Reusing running option analysis service (PID $($existing.Id))."
        return $existing
    }
    Add-Content -Path $supervisorLog -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') Starting option analysis service."
    Start-Process -FilePath $python -ArgumentList @("scripts\cloud_market_runner.py") -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $logDir "analysis.out.log") -RedirectStandardError (Join-Path $logDir "analysis.err.log") -PassThru
}

function Start-CommandListener {
    $existing = Find-RunningServiceProcess "cloud_command_listener\.py"
    if ($existing) {
        Add-Content -Path $supervisorLog -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') Reusing running mobile command listener (PID $($existing.Id))."
        return $existing
    }
    Add-Content -Path $supervisorLog -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') Starting mobile command listener."
    Start-Process -FilePath $python -ArgumentList @("cloud_command_listener.py") -WorkingDirectory $stockRoot -RedirectStandardOutput (Join-Path $logDir "commands.out.log") -RedirectStandardError (Join-Path $logDir "commands.err.log") -PassThru
}

$analysis = Start-AnalysisProcess
$commandListener = Start-CommandListener
try {
    Write-Host "Started free local option analysis and Firebase command listener."

    # Keep the user-session supervisor alive overnight. The option analysis
    # worker exits after the cash session; restart it at the next market open.
    while ($true) {
        Start-Sleep -Seconds 10
        $now = Get-Date
        $marketOpen = $now.Date.AddHours(9).AddMinutes(5)
        $marketClose = $now.Date.AddHours(15).AddMinutes(30)
        if ($analysis.HasExited -and $now -ge $marketOpen -and $now -lt $marketClose) {
            Add-Content -Path $supervisorLog -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') Option service exited with code $($analysis.ExitCode); restarting."
            Start-Sleep -Seconds 5
            $analysis = Start-AnalysisProcess
        }
        if ($commandListener.HasExited) {
            Add-Content -Path $supervisorLog -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') Command listener exited with code $($commandListener.ExitCode); restarting."
            Start-Sleep -Seconds 2
            $commandListener = Start-CommandListener
        }
    }
}
finally {
    if ($commandListener -and -not $commandListener.HasExited) { Stop-Process -Id $commandListener.Id -Force }
    if ($analysis -and -not $analysis.HasExited) { Stop-Process -Id $analysis.Id -Force }
    $sessionMutex.ReleaseMutex()
    $sessionMutex.Dispose()
}
