ALTER TABLE rma_items
    ADD COLUMN IF NOT EXISTS received_location_id UUID;
