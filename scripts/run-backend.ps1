$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$envFile = Join-Path $root ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found at $envFile"
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()

    if ($line -and -not $line.StartsWith("#") -and $line -match '^([^=]+)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

$env:DB_URL = "jdbc:postgresql://localhost:5432/credisynch"
$env:DB_USER = "credisynch"

Write-Host "DB_USER loaded: $($env:DB_USER)"
Write-Host "DB_PASSWORD loaded: $([bool]$env:DB_PASSWORD)"
Write-Host "DB_PASSWORD length: $($env:DB_PASSWORD.Length)"

$env:DB_URL = "jdbc:postgresql://localhost:5432/credisynch"
$env:DB_USER = "credisynch"

Write-Host "DB_URL loaded: $env:DB_URL"
Write-Host "DB_USER loaded after override: $env:DB_USER"
Write-Host "DB_PASSWORD length: $($env:DB_PASSWORD.Length)"

# Force Java 21 for this backend process

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

Write-Host "JAVA_HOME: $env:JAVA_HOME"

Set-Location "$root\backend"

mvn -version

mvn spring-boot:run