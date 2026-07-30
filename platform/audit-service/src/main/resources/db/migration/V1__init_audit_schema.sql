CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    actor VARCHAR(255),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(255),
    raw_payload TEXT NOT NULL,
    CONSTRAINT uq_audit_events_source_event_id UNIQUE (source_event_id)
);
CREATE INDEX idx_audit_events_event_type ON audit_events (event_type, occurred_at DESC);

-- Required by common-messaging's IdempotencyGuard (see shared/common-messaging/README.md)
CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processed_events_event_consumer UNIQUE (event_id, consumer_name)
);

-- Required by common-messaging's OutboxEventStore/OutboxRelayPublisher, which are
-- entity-scanned unconditionally alongside the idempotent-consumer support this
-- service actually uses. Unused today (Audit Service doesn't publish domain
-- events) but its absence fails Hibernate's schema validation at startup.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(255),
    producer VARCHAR(255) NOT NULL,
    schema_version INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
