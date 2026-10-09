-- Migration: V5__add_shedlock_table.sql
-- Description: Add shedlock table for execute sheduled tasks on different instance

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY(name)
);

COMMENT ON COLUMN shedlock.name IS 'Sheduled task name';
COMMENT ON COLUMN shedlock.locked_by IS 'Instance name (hostname or IP) that locked';