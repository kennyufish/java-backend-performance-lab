package com.tszkinyu.performancelab.events;

import java.time.Instant;

public record CustomerEventResponse(
		long id,
		int tenantId,
		long customerId,
		String eventType,
		Instant occurredAt,
		String payload) {

	static CustomerEventResponse from(CustomerEvent event) {
		return new CustomerEventResponse(
				event.getId(),
				event.getTenantId(),
				event.getCustomerId(),
				event.getEventType(),
				event.getOccurredAt(),
				event.getPayload());
	}
}
