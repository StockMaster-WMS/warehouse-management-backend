-- Liên kết putaway với dòng PO (luồng nhận hàng)
ALTER TABLE putaway_tasks
    ADD COLUMN po_item_id UUID REFERENCES po_items (id);

CREATE INDEX idx_putaway_po_item ON putaway_tasks (po_item_id);
