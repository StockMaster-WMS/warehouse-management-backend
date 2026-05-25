ALTER TABLE rma_items
    ADD COLUMN IF NOT EXISTS disposition_action VARCHAR(40),
    ADD COLUMN IF NOT EXISTS disposition_location_id UUID,
    ADD COLUMN IF NOT EXISTS disposition_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disposition_by UUID,
    ADD COLUMN IF NOT EXISTS disposition_note VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_rma_items_disposition_action
    ON rma_items(disposition_action);

CREATE INDEX IF NOT EXISTS idx_rma_items_disposition_location
    ON rma_items(disposition_location_id);
