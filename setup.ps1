<#
.SYNOPSIS
    Sets up the Python virtual environment required by Glean SDK parser for Clarus Browser.
#>

Write-Host "Creating Python virtual environment in ./venv..." -ForegroundColor Cyan
python -m venv venv
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to create python venv. Please ensure Python 3 is installed and in your PATH."
    exit 1
}

Write-Host "Installing glean_parser dependency..." -ForegroundColor Cyan
& ".\venv\Scripts\python.exe" -m pip install --trusted-host pypi.python.org --no-cache-dir "glean_parser~=20.0"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to install glean_parser."
    exit 1
}

Write-Host "Virtual environment setup complete! You can now run: .\gradlew assembleForkDebug" -ForegroundColor Green
