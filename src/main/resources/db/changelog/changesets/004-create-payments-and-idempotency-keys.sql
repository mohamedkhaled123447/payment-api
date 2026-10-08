--liquibase formatted sql

--changeset payverse:004-create-payments-and-idempotency-keys
CREATE TABLE payments (
    id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    capture_method VARCHAR(20) NOT NULL,
    merchant_reference VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0)
);

-- The primary key is the unique constraint that lets only one request per key win.
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    payment_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key),
    CONSTRAINT fk_idempotency_keys_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

-- NOT VALID: sessions created before payments existed point at ids with no payment row.
-- The key is enforced for new rows only.
ALTER TABLE three_ds_sessions
    ADD CONSTRAINT fk_three_ds_sessions_payment
    FOREIGN KEY (payment_id) REFERENCES payments (id) NOT VALID;
