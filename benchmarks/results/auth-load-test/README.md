# Authentication Load-Test Result

This report compares the two authentication paths implemented by this repository. It is a local, synthetic mechanism benchmark, not a production authentication-capacity claim.

## Method

- Tool: Gatling 3.15.1 with the Java DSL
- Application: Spring Boot 4.1.0 on Microsoft OpenJDK 21.0.12
- Load model: 100 new requests per second
- Client pool: 100 deterministic client IDs
- Warm-up: 5 seconds before each measured case
- Measurement: 15 seconds and 1,500 measured requests per case
- Synthetic new-session delay: 20 ms, enabled only by the `load-test` profile
- Assertions: HTTP 200, zero failed requests, correct `reused` value, and p95 below 1,000 ms

The session-reuse warm-up populates the 100-client session store. Its measured requests must return `reused: true`. Baseline requests must return `reused: false` and create a new session every time.

Both cases use the same application process, Gatling client, request rate, payload distribution, machine, and HTTP stack. Gatling reports the warm-up and measured request names separately; the values below are extracted only from the measured request rows.

## Local result

Generated on 2026-08-07 on Windows 11 with an Intel Core i5-13600K, 20 logical processors, and 31.8 GiB visible memory.

| Case | Requests | Failures | Mean | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Authenticate every request | 1,500 | 0 | 34 ms | 33 ms | 37 ms | 51 ms |
| Reuse active session | 1,500 | 0 | 6 ms | 5 ms | 14 ms | 31 ms |

For this paired run, session reuse reduced mean response time by **5.667x** and p95 response time by **2.643x**. These ratios describe this repository's 20 ms synthetic-delay experiment only.

Machine-readable statistics are in [`auth-comparison.json`](auth-comparison.json). The two `simulation.log` files are the raw Gatling inputs from which the HTML reports and summary statistics were generated.

## Reproduce

Windows PowerShell:

```powershell
$env:LAB_DB_PASSWORD = '<your-local-lab-password>'
.\scripts\run-auth-load-test.ps1
```

All load parameters are script options. For example:

```powershell
.\scripts\run-auth-load-test.ps1 `
  -RequestsPerSecond 200 `
  -ClientPoolSize 100 `
  -WarmupSeconds 10 `
  -MeasurementSeconds 30
```

Each run replaces the three versioned result files in this directory. Full Gatling HTML reports remain under `target/gatling/` and are intentionally not committed.

## Limitations

- The 20 ms delay models an upstream authentication round trip; no identity provider or credential verification is present.
- This is one paired run on one local machine, not a saturation or maximum-throughput test.
- The in-memory store is single-process and is pre-warmed before the measured reuse interval.
- Results can change with hardware, JVM state, background load, request rate, client-pool size, TTL, and synthetic delay.
- HTTP connection reuse and application-process state are shared consistently between cases, but the two cases run sequentially rather than simultaneously.
