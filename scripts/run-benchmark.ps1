param(
    [ValidateRange(1, [long]::MaxValue)]
    [long]$Rows = 1000000
)

$ErrorActionPreference = 'Stop'

if (-not $env:LAB_DB_PASSWORD) {
    throw 'Set LAB_DB_PASSWORD before running the benchmark.'
}

$env:LAB_BENCHMARK_ROWS = $Rows
$env:SPRING_PROFILES_ACTIVE = 'benchmark'

& "$PSScriptRoot\..\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
