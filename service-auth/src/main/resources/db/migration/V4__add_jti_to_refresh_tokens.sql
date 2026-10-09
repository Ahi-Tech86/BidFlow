-- Migration: V4__add_jti_to_refresh_tokens.sql
-- Description: Add jti field to refresh_token table for quick search

ALTER TABLE refresh_token ADD COLUMN jti VARCHAR(36) NOT NULL;
ALTER TABLE refresh_token ADD CONSTRAINT uq_refresh_token_jti UNIQUE (jti);

-- Create fast index to JTI field
CREATE INDEX idx_refresh_token_jti ON refresh_token(jti);

-- Delete old index
DROP INDEX idx_refresh_token_token;