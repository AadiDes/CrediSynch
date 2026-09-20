$root = Split-Path -Parent $PSScriptRoot
docker compose -f "$root\infra\docker-compose.yml" down
Write-Host "Infrastructure stopped. Add -v to the compose command to also drop the database volume."
