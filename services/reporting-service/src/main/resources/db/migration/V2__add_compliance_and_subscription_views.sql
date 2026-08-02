CREATE TABLE kyc_check_view (
    check_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    decided_by VARCHAR(255),
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ
);
CREATE INDEX idx_kyc_check_view_customer_id ON kyc_check_view (customer_id);

CREATE TABLE aml_screening_view (
    screening_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    outcome VARCHAR(20),
    notes TEXT,
    failure_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ
);
CREATE INDEX idx_aml_screening_view_customer_id ON aml_screening_view (customer_id);

CREATE TABLE document_view (
    document_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    document_type VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    notes TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ
);
CREATE INDEX idx_document_view_customer_id ON document_view (customer_id);

CREATE TABLE subscription_view (
    subscription_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    portfolio_id UUID,
    fund_code VARCHAR(50),
    quantity NUMERIC(19, 4),
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    reserved_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    timed_out_at TIMESTAMPTZ
);
CREATE INDEX idx_subscription_view_customer_id ON subscription_view (customer_id);
