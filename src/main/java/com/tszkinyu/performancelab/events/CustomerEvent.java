package com.tszkinyu.performancelab.events;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_events")
class CustomerEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false)
	private int tenantId;

	@Column(name = "customer_id", nullable = false)
	private long customerId;

	@Column(name = "event_type", nullable = false, length = 32)
	private String eventType;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false, length = 128)
	private String payload;

	protected CustomerEvent() {
	}

	Long getId() {
		return id;
	}

	int getTenantId() {
		return tenantId;
	}

	long getCustomerId() {
		return customerId;
	}

	String getEventType() {
		return eventType;
	}

	Instant getOccurredAt() {
		return occurredAt;
	}

	String getPayload() {
		return payload;
	}
}
