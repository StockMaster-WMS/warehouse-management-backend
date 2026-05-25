ALTER TABLE rma_items
    ADD COLUMN IF NOT EXISTS supplier_return_rma_id UUID;

CREATE INDEX IF NOT EXISTS idx_rma_items_supplier_return_rma
    ON rma_items(supplier_return_rma_id);
