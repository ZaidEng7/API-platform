CREATE TABLE funds (
    id UUID PRIMARY KEY,
    fund_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_funds_fund_code UNIQUE (fund_code)
);

CREATE TABLE nav_snapshots (
    id UUID PRIMARY KEY,
    fund_code VARCHAR(50) NOT NULL,
    nav_per_share NUMERIC(19, 4) NOT NULL,
    as_of_date DATE NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_nav_snapshots_fund_code ON nav_snapshots (fund_code, as_of_date DESC);

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
