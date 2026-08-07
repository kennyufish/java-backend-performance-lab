package com.tszkinyu.performancelab.events;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/events")
class CustomerEventController {

	private final CustomerEventRepository repository;

	CustomerEventController(CustomerEventRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/recent")
	List<CustomerEventResponse> findRecent(
			@RequestParam int tenantId,
			@RequestParam String eventType,
			@RequestParam Instant from,
			@RequestParam(defaultValue = "100") int limit) {
		if (eventType.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required");
		}
		if (limit < 1 || limit > 1000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 1000");
		}

		return repository.findRecent(tenantId, eventType, from, limit).stream()
				.map(CustomerEventResponse::from)
				.toList();
	}
}
