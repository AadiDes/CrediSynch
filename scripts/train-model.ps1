# Trains the fraud model on the BAF dataset, then restart run-ml.ps1 to pick it up.
param([string]$Data = "C:\data\baf\Base.csv")
$root = Split-Path -Parent $PSScriptRoot
Set-Location "$root\ml-service"
if (-not (Test-Path ".venv")) { py -3.12 -m venv .venv }
.\.venv\Scripts\python.exe -m pip install --quiet -r requirements-ml.txt
.\.venv\Scripts\python.exe training\train_baf.py --data $Data --out models
