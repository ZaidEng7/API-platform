CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    customer_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    portfolio_id UUID NOT NULL,
    fund_code VARCHAR(50) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    timeout_at TIMESTAMPTZ,
    CONSTRAINT uq_subscriptions_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX idx_subscriptions_owner_id ON subscriptions (owner_id);
-- Drives the timeout/dead-letter job's poll (guide §8.4: "every saga has a timeout and a dead-letter path").
CREATE INDEX idx_subscriptions_status_timeout_at ON subscriptions (status, timeout_at);

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
