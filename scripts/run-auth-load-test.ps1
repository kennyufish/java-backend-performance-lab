param(
    [int]$RequestsPerSecond = 100,
    [int]$ClientPoolSize = 100,
    [int]$WarmupSeconds = 5,
    [int]$MeasurementSeconds = 15,
    [string]$OutputDirectory = 'benchmarks\results\auth-load-test'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$applicationLog = Join-Path $projectRoot 'target\auth-load-test-application.log'
$applicationErrorLog = Join-Path $projectRoot 'target\auth-load-test-application-error.log'
$resultRoot = Join-Path $projectRoot $OutputDirectory

function Invoke-GatlingCase {
    param(
        [string]$CaseName,
        [string]$AuthPath,
        [string[]]$CommonArguments
    )

    & .\mvnw.cmd @CommonArguments "-Dlab.load.case-name=$CaseName" "-Dlab.load.auth-path=$AuthPath" | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "$CaseName load test failed."
    }

    return Get-ChildItem 'target\gatling' -Directory |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

function Get-RequestStatistics {
    param(
        [System.IO.DirectoryInfo]$ReportDirectory,
        [string]$RequestName
    )

    $html = Get-Content (Join-Path $ReportDirectory.FullName 'index.html') -Raw
    $requestPosition = $html.IndexOf(">$RequestName</span>", [System.StringComparison]::Ordinal)
    if ($requestPosition -lt 0) {
        throw "Could not find request '$RequestName' in $($ReportDirectory.FullName)."
    }

    $rowStart = $html.LastIndexOf('<tr ', $requestPosition, [System.StringComparison]::Ordinal)
    $rowEnd = $html.IndexOf('</tr>', $requestPosition, [System.StringComparison]::Ordinal)
    $row = $html.Substring($rowStart, $rowEnd + 5 - $rowStart)
    $values = [regex]::Matches($row, '<td class="value [^"]+">([^<]+)</td>') |
        ForEach-Object { $_.Groups[1].Value.Trim() }

    if ($values.Count -ne 13) {
        throw "Expected 13 statistic values for '$RequestName', found $($values.Count)."
    }

    return [ordered]@{
        requestCount = [int]$values[0]
        successfulRequests = [int]$values[1]
        failedRequests = [int]$values[2]
        responseTimeMs = [ordered]@{
            minimum = [int]$values[5]
            percentile50 = [int]$values[6]
            percentile75 = [int]$values[7]
            percentile95 = [int]$values[8]
            percentile99 = [int]$values[9]
            maximum = [int]$values[10]
            mean = [int]$values[11]
            standardDeviation = [int]$values[12]
        }
    }
}

if (-not $env:LAB_DB_PASSWORD) {
    throw 'LAB_DB_PASSWORD must contain the local performance_lab database password.'
}
if ($RequestsPerSecond -le 0 -or $ClientPoolSize -le 0 -or $WarmupSeconds -le 0 -or $MeasurementSeconds -le 0) {
    throw 'Load-test parameters must all be positive integers.'
}
if ($RequestsPerSecond * $WarmupSeconds -lt $ClientPoolSize) {
    throw 'Warm-up must send at least one request for every client in the pool.'
}

Push-Location $projectRoot
try {
    & .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        throw 'Application package failed.'
    }

    $application = Start-Process `
        -FilePath (Join-Path $env:JAVA_HOME 'bin\java.exe') `
        -ArgumentList '-jar', 'target\java-backend-performance-lab-0.0.1-SNAPSHOT.jar', '--spring.profiles.active=load-test' `
        -RedirectStandardOutput $applicationLog `
        -RedirectStandardError $applicationErrorLog `
        -PassThru `
        -WindowStyle Hidden

    try {
        $ready = $false
        foreach ($attempt in 1..60) {
            try {
                $health = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 1
                if ($health.status -eq 'UP') {
                    $ready = $true
                    break
                }
            } catch {
                Start-Sleep -Milliseconds 500
            }
        }
        if (-not $ready) {
            throw "Application did not become healthy. See $applicationLog"
        }

        $common = @(
            '-B',
            "-Dlab.load.requests-per-second=$RequestsPerSecond",
            "-Dlab.load.client-pool-size=$ClientPoolSize",
            "-Dlab.load.warmup-seconds=$WarmupSeconds",
            "-Dlab.load.measurement-seconds=$MeasurementSeconds",
            'gatling:test'
        )

        $baselineReport = Invoke-GatlingCase `
            -CaseName 'baseline' `
            -AuthPath '/api/v1/auth/baseline' `
            -CommonArguments $common
        $reuseReport = Invoke-GatlingCase `
            -CaseName 'session-reuse' `
            -AuthPath '/api/v1/auth/session-reuse' `
            -CommonArguments $common

        $baseline = Get-RequestStatistics $baselineReport 'baseline authentication'
        $reuse = Get-RequestStatistics $reuseReport 'session-reuse authentication'

        New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
        Copy-Item (Join-Path $baselineReport.FullName 'simulation.log') `
            (Join-Path $resultRoot 'baseline-simulation.log') -Force
        Copy-Item (Join-Path $reuseReport.FullName 'simulation.log') `
            (Join-Path $resultRoot 'session-reuse-simulation.log') -Force

        $processor = Get-CimInstance Win32_Processor | Select-Object -First 1
        $operatingSystem = Get-CimInstance Win32_OperatingSystem
        $result = [ordered]@{
            benchmark = 'synthetic-authentication-session-reuse'
            generatedAtUtc = [DateTime]::UtcNow.ToString('o')
            tool = [ordered]@{
                name = 'Gatling'
                version = '3.15.1'
            }
            configuration = [ordered]@{
                requestsPerSecond = $RequestsPerSecond
                clientPoolSize = $ClientPoolSize
                warmupSeconds = $WarmupSeconds
                measurementSeconds = $MeasurementSeconds
                syntheticNewSessionDelay = $(if ($env:LAB_AUTH_NEW_SESSION_DELAY) {
                    $env:LAB_AUTH_NEW_SESSION_DELAY
                } else {
                    '20ms'
                })
            }
            environment = [ordered]@{
                java = (& java -version 2>&1 | Select-Object -First 1).ToString()
                os = $operatingSystem.Caption
                processor = $processor.Name.Trim()
                logicalProcessors = $processor.NumberOfLogicalProcessors
                memoryGiB = [math]::Round($operatingSystem.TotalVisibleMemorySize / 1MB, 1)
            }
            results = @(
                [ordered]@{ case = 'baseline'; statistics = $baseline; rawLog = 'baseline-simulation.log' },
                [ordered]@{ case = 'session-reuse'; statistics = $reuse; rawLog = 'session-reuse-simulation.log' }
            )
            comparison = [ordered]@{
                percentile95Speedup = [math]::Round(
                    $baseline.responseTimeMs.percentile95 / $reuse.responseTimeMs.percentile95,
                    3)
                meanSpeedup = [math]::Round(
                    $baseline.responseTimeMs.mean / $reuse.responseTimeMs.mean,
                    3)
            }
        }
        $result | ConvertTo-Json -Depth 8 |
            Set-Content (Join-Path $resultRoot 'auth-comparison.json') -Encoding utf8
    } finally {
        Stop-Process -Id $application.Id -ErrorAction SilentlyContinue
    }
} finally {
    Pop-Location
}
