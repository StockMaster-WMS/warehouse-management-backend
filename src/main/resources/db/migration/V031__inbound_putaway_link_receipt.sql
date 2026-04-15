-- V6: Liên kết putaway_tasks → inbound_receipts
ALTER TABLE putaway_tasks
    ADD COLUMN inbound_receipt_id UUID REFERENCES inbound_receipts(id);

CREATE INDEX idx_putaway_receipt ON putaway_tasks(inbound_receipt_id);
