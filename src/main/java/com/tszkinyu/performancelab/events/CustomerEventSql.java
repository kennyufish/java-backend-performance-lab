package com.tszkinyu.performancelab.events;

public final class CustomerEventSql {

	public static final String FIND_RECENT = """
			SELECT id, tenant_id, customer_id, event_type, occurred_at, payload
			FROM customer_events
			WHERE tenant_id = :tenantId
			  AND event_type = :eventType
			  AND occurred_at >= :from
			ORDER BY occurred_at DESC
			LIMIT :limit
			""";

	private CustomerEventSql() {
	}
}
