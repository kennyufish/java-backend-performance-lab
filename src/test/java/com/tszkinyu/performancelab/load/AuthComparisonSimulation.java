package com.tszkinyu.performancelab.load;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import java.time.Duration;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

public class AuthComparisonSimulation extends Simulation {

	private static final String BASE_URL = property("lab.load.base-url", "http://localhost:8080");
	private static final String AUTH_PATH = property("lab.load.auth-path", "/api/v1/auth/baseline");
	private static final String CASE_NAME = property("lab.load.case-name", "baseline");
	private static final int REQUESTS_PER_SECOND = integerProperty("lab.load.requests-per-second", 100);
	private static final int CLIENT_POOL_SIZE = integerProperty("lab.load.client-pool-size", 100);
	private static final Duration WARMUP = durationProperty("lab.load.warmup-seconds", 5);
	private static final Duration MEASUREMENT = durationProperty("lab.load.measurement-seconds", 15);
	private static final String MEASURED_REQUEST = CASE_NAME + " authentication";
	private static final boolean EXPECT_REUSED = CASE_NAME.equals("session-reuse");

	private final HttpProtocolBuilder protocol = http
			.baseUrl(BASE_URL)
			.contentTypeHeader("application/json");

	private final ScenarioBuilder warmup = scenario(CASE_NAME + " warm-up")
			.exec(authenticationRequest("warm-up", false));

	private final ScenarioBuilder measurement = scenario(CASE_NAME + " measurement")
			.exec(authenticationRequest(MEASURED_REQUEST, true));

	public AuthComparisonSimulation() {
		setUp(
				warmup.injectOpen(constantUsersPerSec(REQUESTS_PER_SECOND).during(WARMUP))
						.andThen(measurement.injectOpen(constantUsersPerSec(REQUESTS_PER_SECOND).during(MEASUREMENT))))
				.protocols(protocol)
				.assertions(
						details(MEASURED_REQUEST).failedRequests().count().is(0L),
						details(MEASURED_REQUEST).responseTime().percentile(95.0).lt(1_000));
	}

	private HttpRequestActionBuilder authenticationRequest(String requestName, boolean checkReuse) {
		var request = http(requestName)
				.post(AUTH_PATH)
				.body(io.gatling.javaapi.core.CoreDsl.StringBody(session -> "{\"clientId\":\"load-client-"
						+ (session.userId() % CLIENT_POOL_SIZE) + "\"}"))
				.check(status().is(200));

		if (checkReuse) {
			request = request.check(jsonPath("$.reused").is(Boolean.toString(EXPECT_REUSED)));
		}
		return request;
	}

	private static String property(String name, String defaultValue) {
		return System.getProperty(name, defaultValue);
	}

	private static int integerProperty(String name, int defaultValue) {
		return Integer.parseInt(property(name, Integer.toString(defaultValue)));
	}

	private static Duration durationProperty(String name, int defaultSeconds) {
		return Duration.ofSeconds(integerProperty(name, defaultSeconds));
	}
}
