-- Migration: V3__add_refresh_token_constrains_and_indexes.sql
-- Description: Add validation constrain and performance indexes for refresh tokens.

ALTER TABLE refresh_token
ADD CONSTRAINT chk_refresh_token_valid CHECK (expires_at > created_at);

CREATE INDEX idx_refresh_token_user_id_revoked ON refresh_token(user_id, revoked);

CREATE INDEX idx_refresh_token_token ON refresh_token(token);

