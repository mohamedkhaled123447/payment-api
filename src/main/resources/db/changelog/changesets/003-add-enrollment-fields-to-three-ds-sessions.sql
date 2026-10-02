--liquibase formatted sql

--changeset payverse:003-add-enrollment-fields-to-three-ds-sessions
ALTER TABLE three_ds_sessions
    ADD COLUMN amount NUMERIC(19, 4),
    ADD COLUMN currency VARCHAR(3),
    ADD COLUMN authentication_transaction_id VARCHAR(255),
    ADD COLUMN authentication_value VARCHAR(255),
    ADD COLUMN eci VARCHAR(10),
    ADD COLUMN ecommerce_indicator VARCHAR(50),
    ADD COLUMN xid VARCHAR(255),
    ADD COLUMN specification_version VARCHAR(20),
    ADD COLUMN directory_server_transaction_id VARCHAR(255),
    ADD COLUMN veres_enrolled VARCHAR(10),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_three_ds_sessions_payment_id ON three_ds_sessions (payment_id);
