-- Account deletion used to hard-delete every audit_log row for the merchant, which
-- destroyed the fraud/AML trail (including bank-detail-change history) at exactly the
-- moment an actor is most likely to have something worth hiding. GDPR Article 17 erasure
-- only requires removing personal data, not the security/financial record itself, and UK
-- AML expectations are to retain that record.
--
-- Replace the trigger so it still blocks any UPDATE that touches the immutable facts of
-- an event (id, merchant_id, event_type, outcome, created_at, trace_id) but allows an
-- update that only clears the PII-bearing columns (detail, ip_address, user_agent,
-- failure_reason) for erasure. DELETE remains blocked entirely — erasure is now a
-- pseudonymizing UPDATE, not a DELETE.
CREATE OR REPLACE FUNCTION audit_log_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_log rows are immutable — DELETE is not permitted';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id IS DISTINCT FROM OLD.id
            OR NEW.merchant_id IS DISTINCT FROM OLD.merchant_id
            OR NEW.event_type IS DISTINCT FROM OLD.event_type
            OR NEW.outcome IS DISTINCT FROM OLD.outcome
            OR NEW.created_at IS DISTINCT FROM OLD.created_at
            OR NEW.trace_id IS DISTINCT FROM OLD.trace_id THEN
            RAISE EXCEPTION 'audit_log rows are immutable — only detail/ip_address/user_agent/failure_reason may be cleared for erasure';
        END IF;
        RETURN NEW;
    END IF;
    RETURN OLD;
END;
$$;
