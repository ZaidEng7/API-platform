CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    customer_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    -- Opaque PSP-issued reference only — never a raw card/account number
    -- (guide §3.1/§12.5: PCI-DSS scope isolation). See Transfer's Javadoc.
    payment_method_token VARCHAR(255) NOT NULL,
    reference VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX idx_transfers_owner_id ON transfers (owner_id);

-- Required by common-messaging's OutboxEventStore/OutboxRelayPublisher and
-- IdempotencyGuard (see shared/common-messaging/README.md).
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
