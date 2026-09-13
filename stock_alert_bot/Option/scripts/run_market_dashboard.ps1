$ErrorActionPreference = 'Stop'
$projectRoot = 'C:\Users\Kunal\Desktop\Stock\stock_alert_bot'
$port = 5000

$connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($connections) {
    Write-Host "Dashboard already running on port $port. Skipping start."
    exit 0
}

Set-Location $projectRoot
$credentialNames = @('ANGEL_API_KEY', 'ANGEL_CLIENT_CODE', 'ANGEL_PIN', 'ANGEL_TOTP_SECRET')
foreach ($name in $credentialNames) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ($value) {
        Set-Item -Path "Env:$name" -Value $value
    }
}

$python = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) {
    $python = (Get-Command python -ErrorAction Stop).Source
}

& $python .\scripts\web_dashboard.py
exit $LASTEXITCODE
