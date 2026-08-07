CREATE INDEX idx_customer_events_tenant_type_occurred_at
    ON customer_events (tenant_id, event_type, occurred_at DESC);
