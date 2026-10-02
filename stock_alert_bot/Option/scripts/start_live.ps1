# Creates virtualenv, installs dependencies, and reminds to configure settings.json
param(
    [string]$Python = "python",
    [string]$VenvDir = "venv"
)

Write-Host "Creating virtual environment..."
& $Python -m venv $VenvDir
Write-Host "Activating virtual environment..."
& "$VenvDir\Scripts\Activate.ps1"

if (Test-Path requirements.txt) {
    Write-Host "Installing requirements..."
    pip install -r requirements.txt
} else {
    Write-Host "No requirements.txt found. Please create or install dependencies manually."
}

Write-Host "\nMake sure to update config/settings.json (set use_live_api=true and your symbol)."
Write-Host "Run: python step8_scheduler.py"
