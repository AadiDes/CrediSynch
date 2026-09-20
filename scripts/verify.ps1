# End-to-end check of the phase 1 walking skeleton.
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$failures = 0

function Check($name, [scriptblock]$test) {
    try {
        if (& $test) { Write-Host "PASS  $name" -ForegroundColor Green }
        else { Write-Host "FAIL  $name" -ForegroundColor Red; $script:failures++ }
    } catch {
        Write-Host "FAIL  $name ($($_.Exception.Message))" -ForegroundColor Red; $script:failures++
    }
}

Check "Postgres container healthy" { (docker inspect -f '{{.State.Health.Status}}' credisynch-db) -eq "healthy" }
Check "pgvector extension available" {
    (docker exec credisynch-db psql -U credisynch -d credisynch -tAc "SELECT 1 FROM pg_available_extensions WHERE name='vector'") -eq "1"
}
Check "Keycloak realm published" {
    (Invoke-WebRequest "http://localhost:8081/realms/credisynch/.well-known/openid-configuration" -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200
}
Check "API health is UP" {
    ((Invoke-RestMethod "http://localhost:8080/actuator/health" -TimeoutSec 5).status) -eq "UP"
}
Check "API rejects anonymous callers" {
    try { Invoke-WebRequest "http://localhost:8080/api/v1/me" -UseBasicParsing -TimeoutSec 5 | Out-Null; $false }
    catch { $_.Exception.Response.StatusCode.value__ -eq 401 }
}
Check "Analyst token reaches the queue endpoint" {
    $token = & "$root\scripts\get-token.ps1" analyst analyst123
    (Invoke-WebRequest "http://localhost:8080/api/v1/queue/summary" -Headers @{Authorization = "Bearer $token"} -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200
}
Check "Applicant token is refused by the queue endpoint (403)" {
    $token = & "$root\scripts\get-token.ps1" applicant applicant123
    try { Invoke-WebRequest "http://localhost:8080/api/v1/queue/summary" -Headers @{Authorization = "Bearer $token"} -UseBasicParsing -TimeoutSec 5 | Out-Null; $false }
    catch { $_.Exception.Response.StatusCode.value__ -eq 403 }
}
Check "Model service health is UP" {
    ((Invoke-RestMethod "http://localhost:8000/health" -TimeoutSec 5).status) -eq "UP"
}

Write-Host ""
if ($failures -eq 0) { Write-Host "Phase 1 verified." -ForegroundColor Green }
else { Write-Host "$failures check(s) failed." -ForegroundColor Red; exit 1 }
