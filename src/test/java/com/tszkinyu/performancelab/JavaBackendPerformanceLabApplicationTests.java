package com.tszkinyu.performancelab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class JavaBackendPerformanceLabApplicationTests {

	private static final Pattern SESSION_ID = Pattern.compile("\\\"sessionId\\\":\\\"([^\\\"]+)\\\"");

	@LocalServerPort
	private int port;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void healthEndpointReportsUp() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri("/actuator/health")).GET().build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"status\":\"UP\""));
	}

	@Test
	void baselineCreatesANewSessionForEveryRequest() throws IOException, InterruptedException {
		String clientId = UUID.randomUUID().toString();

		HttpResponse<String> first = authenticate("/api/v1/auth/baseline", clientId);
		HttpResponse<String> second = authenticate("/api/v1/auth/baseline", clientId);

		assertEquals(200, first.statusCode());
		assertEquals(200, second.statusCode());
		assertNotEquals(sessionId(first.body()), sessionId(second.body()));
		assertTrue(first.body().contains("\"reused\":false"));
		assertTrue(second.body().contains("\"reused\":false"));
	}

	@Test
	void sessionReuseReturnsTheActiveSession() throws IOException, InterruptedException {
		String clientId = UUID.randomUUID().toString();

		HttpResponse<String> first = authenticate("/api/v1/auth/session-reuse", clientId);
		HttpResponse<String> second = authenticate("/api/v1/auth/session-reuse", clientId);

		assertEquals(200, first.statusCode());
		assertEquals(200, second.statusCode());
		assertEquals(sessionId(first.body()), sessionId(second.body()));
		assertTrue(first.body().contains("\"reused\":false"));
		assertTrue(second.body().contains("\"reused\":true"));
	}

	@Test
	void blankClientIdIsRejected() throws IOException, InterruptedException {
		HttpResponse<String> response = authenticate("/api/v1/auth/baseline", " ");

		assertEquals(400, response.statusCode());
	}

	@Test
	void recentEventsEndpointReadsFromPostgresThroughJpa() throws IOException, InterruptedException {
		int tenantId = 900_001;
		jdbcTemplate.update("""
				INSERT INTO customer_events (tenant_id, customer_id, event_type, occurred_at, payload)
				VALUES (?, ?, ?, ?, ?)
				""",
				tenantId,
				123_456L,
				"PURCHASE",
				Timestamp.from(Instant.parse("2025-02-01T00:00:00Z")),
				"phase-2-integration-test");

		try {
			HttpRequest request = HttpRequest.newBuilder(uri(
					"/api/v1/events/recent?tenantId=" + tenantId
							+ "&eventType=PURCHASE&from=2025-01-01T00:00:00Z&limit=10"))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			assertEquals(200, response.statusCode());
			assertTrue(response.body().contains("\"payload\":\"phase-2-integration-test\""));
			assertTrue(response.body().contains("\"tenantId\":" + tenantId));
		} finally {
			jdbcTemplate.update("DELETE FROM customer_events WHERE tenant_id = ?", tenantId);
		}
	}

	@Test
	void recentEventsEndpointRejectsExcessiveLimit() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri(
				"/api/v1/events/recent?tenantId=42&eventType=PURCHASE"
						+ "&from=2025-01-01T00:00:00Z&limit=1001"))
				.GET()
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(400, response.statusCode());
	}

	private HttpResponse<String> authenticate(String path, String clientId)
			throws IOException, InterruptedException {
		String body = "{\"clientId\":\"" + clientId + "\"}";
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private String sessionId(String responseBody) {
		Matcher matcher = SESSION_ID.matcher(responseBody);
		assertTrue(matcher.find(), "Expected sessionId in response: " + responseBody);
		return matcher.group(1);
	}
}
