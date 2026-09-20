# Creates the venv on first run, then starts the model service on port 8000.
$root = Split-Path -Parent $PSScriptRoot
Set-Location "$root\ml-service"
if (-not (Test-Path ".venv")) { py -3.12 -m venv .venv }
.\.venv\Scripts\python.exe -m pip install --quiet --upgrade pip
.\.venv\Scripts\python.exe -m pip install --quiet -r requirements-dev.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
