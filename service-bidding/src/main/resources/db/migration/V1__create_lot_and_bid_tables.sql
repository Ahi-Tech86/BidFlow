-- Migration: V1__create_lot_and_bid_tables.sql
-- Description: Initial script for storing lots and biddings

CREATE TABLE lot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    start_price NUMERIC(13, 2) NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    min_bid_step NUMERIC(10, 2) NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- CONSTRAINTS
    CONSTRAINT chk_lot_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'FINISHED'))
);

CREATE TABLE bid (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id UUID NOT NULL,
    user_id UUID NOT NULL,
    bid_value NUMERIC(13, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    -- CONSTRAINTS
    CONSTRAINT fk_bid_lot FOREIGN KEY (lot_id) REFERENCES lot(id)
);

CREATE INDEX idx_bid_lot_id ON bid(lot_id, created_at DESC);
CREATE INDEX idx_lot_status ON lot(status, finished_at);
CREATE INDEX idx_lot_owner_id ON lot(owner_id);
