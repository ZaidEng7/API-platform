CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_portfolios_owner_id ON portfolios (owner_id);

CREATE TABLE positions (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL,
    fund_code VARCHAR(50) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_positions_portfolio_id ON positions (portfolio_id);

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
