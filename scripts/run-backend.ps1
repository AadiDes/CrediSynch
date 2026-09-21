$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Spring Boot is compiled for Java 21. A machine can have several JDKs on
# PATH, so make the compiler and the forked application use the same runtime.
$javaCandidates = @(
    $env:JAVA_HOME
    if ($env:ProgramFiles) {
        Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles "Eclipse Adoptium") -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty FullName
        Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles "Microsoft") -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty FullName
    }
) | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ "bin\java.exe")) }

$javaHome21 = $null
foreach ($candidate in $javaCandidates) {
    $versionOutput = (& (Join-Path $candidate "bin\java.exe") -version 2>&1 | Out-String)
    if ($versionOutput -match 'version\s+"21(?:\.|"|-)') {
        $javaHome21 = $candidate
        break
    }
}

if (-not $javaHome21) {
    throw "Java 21 is required to run the backend. Set JAVA_HOME to a JDK 21 installation and retry."
}

$env:JAVA_HOME = $javaHome21
$env:Path = "$(Join-Path $javaHome21 'bin');$env:Path"
Write-Host "[INFO] Using Java 21 from $env:JAVA_HOME"

$envFile = Join-Path $root ".env"
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith("#") -and $trimmed -match '^([^=]+)=(.*)$') {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

if (-not $env:DB_URL) { $env:DB_URL = "jdbc:postgresql://localhost:5432/credisynch" }
if (-not $env:DB_USER) { $env:DB_USER = "credisynch" }
if (-not $env:DB_PASSWORD -and $env:POSTGRES_PASSWORD) { $env:DB_PASSWORD = $env:POSTGRES_PASSWORD }

Set-Location "$root\backend"
mvn spring-boot:run
