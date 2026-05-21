ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS return_type VARCHAR(20) DEFAULT 'CUSTOMER';
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS supplier_id UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS supplier_name VARCHAR(200);
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS rejected_by UUID;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ;
ALTER TABLE rma_headers ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);

ALTER TABLE rma_items ADD COLUMN IF NOT EXISTS return_location_id UUID;

UPDATE rma_headers SET return_type = 'CUSTOMER' WHERE return_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_rma_return_type ON rma_headers(return_type);
CREATE INDEX IF NOT EXISTS idx_rma_supplier ON rma_headers(supplier_id);
CREATE INDEX IF NOT EXISTS idx_rma_approved_at ON rma_headers(approved_at DESC);
