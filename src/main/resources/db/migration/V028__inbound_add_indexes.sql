-- Indexes for PurchaseOrder list filters and default sort
CREATE INDEX idx_po_status       ON purchase_orders(status);
CREATE INDEX idx_po_warehouse    ON purchase_orders(warehouse_id);
CREATE INDEX idx_po_supplier     ON purchase_orders(supplier_id);
CREATE INDEX idx_po_created_at   ON purchase_orders(created_at DESC);

-- Index for PutawayTask global status filter
CREATE INDEX idx_putaway_status  ON putaway_tasks(status);
