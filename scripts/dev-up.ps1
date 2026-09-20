# Starts local infrastructure (Postgres + pgvector, Keycloak) and waits for both to be ready.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "Starting infrastructure..." -ForegroundColor Cyan
docker compose -f "$root\infra\docker-compose.yml" up -d

Write-Host "Waiting for Postgres..." -NoNewline
for ($i = 0; $i -lt 60; $i++) {
    $state = docker inspect -f '{{.State.Health.Status}}' credisynch-db 2>$null
    if ($state -eq "healthy") { Write-Host " ready" -ForegroundColor Green; break }
    Start-Sleep -Seconds 2; Write-Host "." -NoNewline
}

Write-Host "Waiting for Keycloak realm import..." -NoNewline
for ($i = 0; $i -lt 90; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8081/realms/credisynch/.well-known/openid-configuration" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { Write-Host " ready" -ForegroundColor Green; break }
    } catch { Start-Sleep -Seconds 2; Write-Host "." -NoNewline }
}

Write-Host ""
Write-Host "Postgres  : localhost:5432 (db=credisynch user=credisynch)"
Write-Host "Keycloak  : http://localhost:8081  (admin console: admin / admin)"
Write-Host "Demo users: analyst/analyst123, applicant/applicant123, platform-admin/admin123"
