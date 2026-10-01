-- Migration: V1__init_app_user_table.sql
-- Description: Initial schema for Auth & User Service

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(65) NOT NULL,
    first_name VARCHAR(25) NOT NULL,
    last_name VARCHAR(25) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    password VARCHAR(255) NOT NULL,

    -- CONSTRAINTS
    CONSTRAINT uq_app_user_email UNIQUE(email),
    CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BLOCKED')),
    CONSTRAINT chk_app_user_role CHECK (role IN ('ROLE_USER', 'ROLE_MODERATOR', 'ROLE_ADMIN'))
);