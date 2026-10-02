$ErrorActionPreference = "Stop"

$optionRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $optionRoot
$python = Join-Path $workspaceRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) {
    $python = (Get-Command python -ErrorAction Stop).Source
}

Set-Location $workspaceRoot
& $python "stock\cloud_command_listener.py"
