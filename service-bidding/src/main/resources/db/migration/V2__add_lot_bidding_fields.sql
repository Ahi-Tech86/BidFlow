-- Migration: V2__add_lot_bidding_fields.sql
-- Description: 
--      Adding new fields to the lot table to store the current max bid value
--      and the ID of the user who made highest bid. This avoids querying
--      the database every time to calculate the current max bid.

ALTER TABLE lot ADD COLUMN current_max_bid NUMERIC(13, 2) NOT NULL;
ALTER TABLE lot ADD COLUMN highest_bidder_id UUID;