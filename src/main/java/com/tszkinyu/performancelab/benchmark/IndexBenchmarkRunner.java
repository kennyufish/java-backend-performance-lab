package com.tszkinyu.performancelab.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tszkinyu.performancelab.events.CustomerEventSql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("benchmark")
class IndexBenchmarkRunner implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(IndexBenchmarkRunner.class);
	private static final String INDEX_NAME = "idx_customer_events_tenant_type_occurred_at";
	private static final String CREATE_INDEX = """
			CREATE INDEX IF NOT EXISTS idx_customer_events_tenant_type_occurred_at
			ON customer_events (tenant_id, event_type, occurred_at DESC)
			""";
	private static final String BENCHMARK_QUERY = CustomerEventSql.FIND_RECENT
			.replace(":tenantId", "42")
			.replace(":eventType", "'PURCHASE'")
			.replace(":from", "TIMESTAMPTZ '2025-01-01 00:00:00+00'")
			.replace(":limit", "100");
	private static final Pattern EXECUTION_TIME = Pattern.compile("\\\"Execution Time\\\"\\s*:\\s*([0-9.]+)");
	private static final Pattern NODE_TYPE = Pattern.compile("\\\"Node Type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
	private static final Pattern INDEX_USED = Pattern.compile("\\\"Index Name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

	private final JdbcTemplate jdbcTemplate;
	private final ConfigurableApplicationContext applicationContext;
	private final long rows;
	private final int warmupRuns;
	private final int measuredRuns;
	private final Path outputDirectory;

	IndexBenchmarkRunner(
			JdbcTemplate jdbcTemplate,
			ConfigurableApplicationContext applicationContext,
			@Value("${lab.benchmark.rows}") long rows,
			@Value("${lab.benchmark.warmup-runs}") int warmupRuns,
			@Value("${lab.benchmark.measured-runs}") int measuredRuns,
			@Value("${lab.benchmark.output-directory}") String outputDirectory) {
		this.jdbcTemplate = jdbcTemplate;
		this.applicationContext = applicationContext;
		this.rows = rows;
		this.warmupRuns = warmupRuns;
		this.measuredRuns = measuredRuns;
		this.outputDirectory = Path.of(outputDirectory);
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		validateConfiguration();
		String postgresVersion = jdbcTemplate.queryForObject("SELECT version()", String.class);
		long loadStarted = System.nanoTime();

		try {
			jdbcTemplate.execute("DROP INDEX IF EXISTS " + INDEX_NAME);
			jdbcTemplate.execute("TRUNCATE TABLE customer_events RESTART IDENTITY");
			loadDataset();
			long loadTimeMs = (System.nanoTime() - loadStarted) / 1_000_000;
			jdbcTemplate.execute("ANALYZE customer_events");

			Measurement before = measure();
			jdbcTemplate.execute(CREATE_INDEX);
			jdbcTemplate.execute("ANALYZE customer_events");
			Measurement after = measure();

			writeResults(postgresVersion, loadTimeMs, before, after);
			logger.info("Benchmark complete: {} rows, median {} ms before, {} ms after",
					rows, format(before.medianMs()), format(after.medianMs()));
		} finally {
			jdbcTemplate.execute(CREATE_INDEX);
			applicationContext.close();
		}
	}

	private void loadDataset() {
		jdbcTemplate.update("""
				INSERT INTO customer_events (tenant_id, customer_id, event_type, occurred_at, payload)
				SELECT
				    (((n - 1) % 100) + 1)::integer,
				    (((n - 1) % 100000) + 1)::bigint,
				    (ARRAY['LOGIN', 'PURCHASE', 'PROFILE_UPDATE', 'LOGOUT'])[
				        ((((n - 1) / 100) % 4) + 1)::integer
				    ],
				    TIMESTAMPTZ '2025-01-01 00:00:00+00' + ((n - 1) * INTERVAL '1 second'),
				    'event-' || lpad(n::text, 10, '0') || '-generated-performance-lab'
				FROM generate_series(1, CAST(? AS bigint)) AS generated(n)
				""", rows);
	}

	private Measurement measure() {
		for (int run = 0; run < warmupRuns; run++) {
			explain();
		}

		List<Double> executionTimes = new ArrayList<>();
		String representativePlan = null;
		for (int run = 0; run < measuredRuns; run++) {
			representativePlan = explain();
			executionTimes.add(executionTime(representativePlan));
		}

		return new Measurement(
				executionTimes,
				median(executionTimes),
				scanNode(representativePlan),
				indexName(representativePlan),
				representativePlan);
	}

	private String explain() {
		return jdbcTemplate.queryForObject(
				"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + BENCHMARK_QUERY,
				(resultSet, rowNumber) -> resultSet.getString(1));
	}

	private double executionTime(String plan) {
		Matcher matcher = EXECUTION_TIME.matcher(plan);
		if (!matcher.find()) {
			throw new IllegalStateException("Execution Time is missing from PostgreSQL plan");
		}
		return Double.parseDouble(matcher.group(1));
	}

	private String scanNode(String plan) {
		Matcher matcher = NODE_TYPE.matcher(plan);
		while (matcher.find()) {
			if (matcher.group(1).contains("Scan")) {
				return matcher.group(1);
			}
		}
		return "unknown";
	}

	private String indexName(String plan) {
		Matcher matcher = INDEX_USED.matcher(plan);
		return matcher.find() ? matcher.group(1) : "none";
	}

	private double median(List<Double> values) {
		List<Double> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int middle = sorted.size() / 2;
		return sorted.size() % 2 == 0
				? (sorted.get(middle - 1) + sorted.get(middle)) / 2
				: sorted.get(middle);
	}

	private void writeResults(
			String postgresVersion,
			long loadTimeMs,
			Measurement before,
			Measurement after) throws IOException {
		Files.createDirectories(outputDirectory);
		double speedup = before.medianMs() / after.medianMs();
		String generatedAt = Instant.now().toString();
		String json = """
				{
				  "generatedAt": %s,
				  "environment": {
				    "postgres": %s,
				    "java": %s,
				    "os": %s,
				    "availableProcessors": %d
				  },
				  "method": {
				    "datasetRows": %d,
				    "loadTimeMs": %d,
				    "warmupRuns": %d,
				    "measuredRuns": %d,
				    "query": %s
				  },
				  "beforeIndex": %s,
				  "afterIndex": %s,
				  "medianSpeedup": %s
				}
				""".formatted(
				jsonString(generatedAt),
				jsonString(postgresVersion),
				jsonString(System.getProperty("java.version")),
				jsonString(System.getProperty("os.name") + " " + System.getProperty("os.version")
						+ " " + System.getProperty("os.arch")),
				Runtime.getRuntime().availableProcessors(),
				rows,
				loadTimeMs,
				warmupRuns,
				measuredRuns,
				jsonString(BENCHMARK_QUERY.strip()),
				measurementJson(before),
				measurementJson(after),
				format(speedup));

		String markdown = """
				# PostgreSQL composite-index benchmark

				Generated: `%s`

				## Result

				| Case | Scan node | Index | Execution times (ms) | Median (ms) |
				| --- | --- | --- | --- | ---: |
				| Before index | %s | %s | %s | %s |
				| After index | %s | %s | %s | %s |

				Median speedup: **%sx**

				## Method

				- Dataset: %,d deterministic rows generated by PostgreSQL `generate_series`.
				- Query target: tenant `42`, event type `PURCHASE`, events since `2025-01-01T00:00:00Z`, limit `100`.
				- Runs: %d warm-up runs followed by %d measured runs per case.
				- Measurement: `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` execution time.
				- PostgreSQL: `%s`
				- Java: `%s`
				- OS: `%s`
				- Data load time: %,d ms.

				## Limitations

				This is one local-machine result, not a production capacity claim. Cache state, storage, CPU, PostgreSQL settings, dataset shape, and concurrent workload can materially change the result. The raw representative plans are preserved in `index-comparison.json`.
				""".formatted(
				generatedAt,
				before.scanNode(), before.indexName(), before.executionTimesMs(), format(before.medianMs()),
				after.scanNode(), after.indexName(), after.executionTimesMs(), format(after.medianMs()),
				format(speedup),
				rows, warmupRuns, measuredRuns,
				postgresVersion,
				System.getProperty("java.version"),
				System.getProperty("os.name") + " " + System.getProperty("os.version")
						+ " " + System.getProperty("os.arch"),
				loadTimeMs);

		Files.writeString(outputDirectory.resolve("index-comparison.json"), json, StandardCharsets.UTF_8);
		Files.writeString(outputDirectory.resolve("README.md"), markdown, StandardCharsets.UTF_8);
	}

	private String measurementJson(Measurement measurement) {
		return """
				{
				    "executionTimesMs": %s,
				    "medianExecutionTimeMs": %s,
				    "scanNode": %s,
				    "indexName": %s,
				    "representativePlan": %s
				  }""".formatted(
				measurement.executionTimesMs(),
				format(measurement.medianMs()),
				jsonString(measurement.scanNode()),
				jsonString(measurement.indexName()),
				measurement.representativePlan());
	}

	private String jsonString(String value) {
		return "\"" + value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n") + "\"";
	}

	private String format(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private void validateConfiguration() {
		if (rows < 1 || warmupRuns < 0 || measuredRuns < 1) {
			throw new IllegalArgumentException("rows and measured runs must be positive; warm-up runs cannot be negative");
		}
	}

	private record Measurement(
			List<Double> executionTimesMs,
			double medianMs,
			String scanNode,
			String indexName,
			String representativePlan) {
	}
}
