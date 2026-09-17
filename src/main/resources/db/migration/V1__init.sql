CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE merchants (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                           VARCHAR(255) NOT NULL UNIQUE,
    password_hash                   VARCHAR(255),
    business_name                   VARCHAR(255) NOT NULL,
    created_at                      TIMESTAMP NOT NULL DEFAULT now(),
    verified                        BOOLEAN NOT NULL DEFAULT false,
    verification_token              VARCHAR(255),
    verification_token_expires_at   TIMESTAMP,
    institution_id                  VARCHAR(100),
    payee_name                      VARCHAR(255),
    sort_code                       VARCHAR(10),
    account_number                  VARCHAR(20),
    password_reset_token            VARCHAR(255),
    password_reset_token_expires_at TIMESTAMP,
    login_attempts                  INT NOT NULL DEFAULT 0,
    locked_until                    TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE payments (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount                          BIGINT NOT NULL,
    currency                        VARCHAR(10) NOT NULL DEFAULT 'GBP',
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_payment_id             VARCHAR(255) UNIQUE,
    merchant_id                     UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    expires_at                      TIMESTAMP NOT NULL,
    created_at                      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMP NOT NULL DEFAULT now(),
    payment_access_token_hash       VARCHAR(255),
    payment_access_token_expires_at TIMESTAMP,
    note                            VARCHAR(255),
    flow_trace_id                   VARCHAR(36)
);

-- Audit log table — append-only record of all sensitive operations.
-- No FK to merchants: audit rows are deleted explicitly in application code
-- before merchant deletion, so no cascade mechanism should touch them.
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID,
    event_type     VARCHAR(64) NOT NULL,
    detail         TEXT,
    ip_address     VARCHAR(64),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    trace_id       VARCHAR(36),
    actor_type     VARCHAR(16),   -- MERCHANT | SYSTEM | WEBHOOK
    outcome        VARCHAR(8),    -- SUCCESS | FAILURE
    failure_reason TEXT,
    user_agent     TEXT
);

-- Immutability: block UPDATE on audit_log rows.
-- DELETE is allowed (needed for GDPR erasure); only UPDATE breaks the
-- append-only integrity guarantee.
CREATE OR REPLACE FUNCTION audit_log_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit_log rows are immutable — UPDATE is not permitted';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_token_hash ON payments(payment_access_token_hash);
CREATE INDEX idx_payments_flow_trace_id ON payments(flow_trace_id);

CREATE INDEX idx_audit_log_merchant_id ON audit_log(merchant_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX idx_audit_log_trace_id ON audit_log(trace_id);

-- refresh_tokens.token_hash is looked up on every /auth/refresh call (findByTokenHash).
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);

-- merchants.verification_token / password_reset_token are looked up on every
-- complete-registration / reset-password request. Partial indexes since these
-- columns are NULL most of the time (nulled out immediately after use or by
-- DataRetentionScheduler on expiry).
CREATE INDEX idx_merchants_verification_token
    ON merchants(verification_token) WHERE verification_token IS NOT NULL;
CREATE INDEX idx_merchants_password_reset_token
    ON merchants(password_reset_token) WHERE password_reset_token IS NOT NULL;
