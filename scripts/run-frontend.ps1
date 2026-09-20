$root = Split-Path -Parent $PSScriptRoot
Set-Location "$root\frontend"
if (-not (Test-Path "node_modules")) { npm install --no-audit --no-fund }
if (-not (Test-Path ".env")) { Copy-Item .env.example .env }
npm run dev
