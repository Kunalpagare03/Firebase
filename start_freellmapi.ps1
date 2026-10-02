Write-Host "Starting FreeLLMAPI Server on http://localhost:3001..." -ForegroundColor Cyan
Set-Location -Path "\freellmapi\server"
node dist\index.js
