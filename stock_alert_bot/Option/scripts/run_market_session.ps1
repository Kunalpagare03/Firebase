param(
    [string]$TunnelHostname = $env:DASHBOARD_PUBLIC_HOSTNAME,
    [string]$TunnelName = $env:DASHBOARD_TUNNEL_NAME,
    [int]$Port = 5000,
    [string]$Cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$python = Join-Path (Split-Path -Parent $projectRoot) ".venv\Scripts\python.exe"
$logDir = Join-Path $projectRoot "logs\scheduled"
New-Item -ItemType Directory -Force $logDir | Out-Null

if (-not (Test-Path $python)) {
    throw "Python environment not found: $python"
}
if (-not (Test-Path $Cloudflared)) {
    $Cloudflared = (Get-Command cloudflared -ErrorAction Stop).Source
}

$credentialNames = @('ANGEL_API_KEY', 'ANGEL_CLIENT_CODE', 'ANGEL_PIN', 'ANGEL_TOTP_SECRET')
foreach ($name in $credentialNames) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) { Set-Item -Path "Env:$name" -Value $value }
}

$dashboard = Start-Process -FilePath $python -ArgumentList @("scripts\web_dashboard.py") -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $logDir "dashboard.out.log") -RedirectStandardError (Join-Path $logDir "dashboard.err.log") -PassThru
try {
    Start-Sleep -Seconds 5
    if ($TunnelName -and $TunnelHostname) {
        Write-Host "Starting permanent Cloudflare tunnel: $TunnelName"
        $tunnel = Start-Process -FilePath $Cloudflared -ArgumentList @("tunnel", "run", $TunnelName) -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $logDir "cloudflared.out.log") -RedirectStandardError (Join-Path $logDir "cloudflared.err.log") -PassThru
        $publicUrl = "https://$TunnelHostname"
    } else {
        Write-Host "Starting temporary Cloudflare quick tunnel"
        $tunnel = Start-Process -FilePath $Cloudflared -ArgumentList @("tunnel", "--url", "http://localhost:$Port") -WorkingDirectory $projectRoot -RedirectStandardOutput (Join-Path $logDir "cloudflared.out.log") -RedirectStandardError (Join-Path $logDir "cloudflared.err.log") -PassThru
        $publicUrl = $null
    }

    Write-Host "Dashboard started at http://localhost:$Port"
    if ($publicUrl) { Write-Host "Permanent mobile URL: $publicUrl" }
    else { Write-Host "Quick tunnel URL is in $logDir\cloudflared.out.log" }

    while ((Get-Date).TimeOfDay -lt ([TimeSpan]::Parse("15:35"))) {
        Start-Sleep -Seconds 30
        if ($dashboard.HasExited -or $tunnel.HasExited) { break }
    }
}
finally {
    if ($tunnel -and -not $tunnel.HasExited) { Stop-Process -Id $tunnel.Id -Force }
    if ($dashboard -and -not $dashboard.HasExited) { Stop-Process -Id $dashboard.Id -Force }
}