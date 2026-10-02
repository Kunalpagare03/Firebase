param(
    [string]$TunnelName = $env:DASHBOARD_TUNNEL_NAME,
    [string]$TunnelHostname = $env:DASHBOARD_PUBLIC_HOSTNAME,
    [int]$Port = 5000,
    [int]$Days = 31,
    [string]$Cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $projectRoot
$python = Join-Path $workspaceRoot ".venv\Scripts\python.exe"
$logDir = Join-Path $projectRoot "logs\monthly-tunnel"

New-Item -ItemType Directory -Force $logDir | Out-Null

if (-not (Test-Path $python)) {
    throw "Python environment not found: $python"
}
if (-not (Test-Path $Cloudflared)) {
    $Cloudflared = (Get-Command cloudflared -ErrorAction Stop).Source
}
if (-not $TunnelName -or -not $TunnelHostname) {
    throw "Set DASHBOARD_TUNNEL_NAME and DASHBOARD_PUBLIC_HOSTNAME before starting the monthly tunnel."
}

$configPath = Join-Path $HOME ".cloudflared\config.yml"
if (-not (Test-Path $configPath)) {
    throw "Cloudflare is not authenticated. Run: cloudflared tunnel login"
}

$dashboard = $null
$tunnel = $null
try {
    $dashboard = Start-Process -FilePath $python `
        -ArgumentList @("scripts\web_dashboard.py") `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput (Join-Path $logDir "dashboard.out.log") `
        -RedirectStandardError (Join-Path $logDir "dashboard.err.log") `
        -PassThru

    Start-Sleep -Seconds 5

    $tunnel = Start-Process -FilePath $Cloudflared `
        -ArgumentList @("tunnel", "run", $TunnelName) `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput (Join-Path $logDir "cloudflared.out.log") `
        -RedirectStandardError (Join-Path $logDir "cloudflared.err.log") `
        -PassThru

    $expiresAt = (Get-Date).AddDays($Days)
    Write-Host "Dashboard: http://localhost:$Port"
    Write-Host "Mobile URL: https://$TunnelHostname"
    Write-Host "Tunnel will remain running until: $expiresAt"
    Write-Host "Logs: $logDir"

    while ((Get-Date) -lt $expiresAt) {
        Start-Sleep -Seconds 30
        if ($dashboard.HasExited) { throw "Dashboard exited with code $($dashboard.ExitCode)." }
        if ($tunnel.HasExited) { throw "Cloudflare tunnel exited with code $($tunnel.ExitCode)." }
    }
}
finally {
    if ($tunnel -and -not $tunnel.HasExited) { Stop-Process -Id $tunnel.Id -Force }
    if ($dashboard -and -not $dashboard.HasExited) { Stop-Process -Id $dashboard.Id -Force }
}
