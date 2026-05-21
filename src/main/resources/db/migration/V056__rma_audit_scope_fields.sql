ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS received_by UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS completed_by UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS cancelled_by UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_rma_warehouse_status ON rma_headers(warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_rma_created_at ON rma_headers(created_at DESC);
