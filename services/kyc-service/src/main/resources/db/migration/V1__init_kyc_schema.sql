CREATE TABLE kyc_checks (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    decided_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_kyc_checks_customer_id ON kyc_checks (customer_id, created_at DESC);

-- Required by common-messaging's OutboxEventStore/OutboxRelayPublisher and
-- IdempotencyGuard (see shared/common-messaging/README.md). This service
-- both publishes (customer.kyc.requested/approved/rejected) and, per that
-- README, needs processed_events entity-scanned regardless.
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

CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processed_events_event_consumer UNIQUE (event_id, consumer_name)
);
