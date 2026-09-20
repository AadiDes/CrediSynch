$root = Split-Path -Parent $PSScriptRoot
Set-Location "$root\backend"
mvn spring-boot:run
