--liquibase formatted sql

--changeset payverse:002-create-three-ds-sessions-table
CREATE TABLE three_ds_sessions (
    id UUID NOT NULL,
    payment_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_reference_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_three_ds_sessions PRIMARY KEY (id)
);
