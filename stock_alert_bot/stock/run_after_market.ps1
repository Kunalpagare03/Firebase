$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path (Split-Path -Parent $root) ".venv\Scripts\python.exe"
Push-Location $root
try { & $python .\stock_scanner.py } finally { Pop-Location }