CREATE TABLE fund_nav_view (
    fund_code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255),
    currency VARCHAR(3),
    nav_per_share NUMERIC(19, 6),
    as_of_date DATE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE portfolio_view (
    portfolio_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_portfolio_view_owner_id ON portfolio_view (owner_id);

CREATE TABLE position_view (
    position_id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL,
    fund_code VARCHAR(50) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_position_view_portfolio_id ON position_view (portfolio_id);

CREATE TABLE payment_transfer_view (
    transfer_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    amount NUMERIC(19, 2),
    currency VARCHAR(3),
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ
);
CREATE INDEX idx_payment_transfer_view_customer_id ON payment_transfer_view (customer_id);

-- Required by common-messaging's IdempotencyGuard (see shared/common-messaging/README.md).
CREATE TABLE processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_processed_events_event_consumer UNIQUE (event_id, consumer_name)
);

-- Required by common-messaging's OutboxEventStore/OutboxRelayPublisher, which are
-- entity-scanned unconditionally alongside the idempotent-consumer support this
-- service actually uses. Unused today (Reporting Service doesn't publish domain
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
