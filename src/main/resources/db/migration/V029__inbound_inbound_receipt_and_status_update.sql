-- ============================================================
-- V4: Inbound Receipt (Phiếu nhập kho) + Status refactor
-- ============================================================

-- 1. Migrate PO statuses: RECEIVING → APPROVED, RECEIVED → COMPLETED
UPDATE purchase_orders SET status = 'APPROVED'  WHERE status = 'RECEIVING';
UPDATE purchase_orders SET status = 'COMPLETED' WHERE status = 'RECEIVED';

-- 2. Table: inbound_receipts (phiếu nhập kho)
CREATE TABLE inbound_receipts (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number  VARCHAR(30)   UNIQUE NOT NULL,
    po_id           UUID          NOT NULL REFERENCES purchase_orders(id),
    warehouse_id    UUID          NOT NULL,
    location_id     UUID          NOT NULL,        -- vị trí nhận hàng (receiving dock)
    note            TEXT,
    received_date   DATE          NOT NULL,
    received_by     UUID,
    created_at      TIMESTAMPTZ   DEFAULT now()
);

CREATE INDEX idx_receipt_po       ON inbound_receipts(po_id);
CREATE INDEX idx_receipt_warehouse ON inbound_receipts(warehouse_id);
CREATE INDEX idx_receipt_date     ON inbound_receipts(received_date DESC);

-- 3. Table: inbound_receipt_items (dòng phiếu nhập)
CREATE TABLE inbound_receipt_items (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id      UUID          NOT NULL REFERENCES inbound_receipts(id),
    po_item_id      UUID          NOT NULL REFERENCES po_items(id),
    product_id      UUID          NOT NULL,
    product_sku     VARCHAR(50)   NOT NULL,
    received_qty    INTEGER       NOT NULL CHECK (received_qty > 0),
    note            TEXT
);

CREATE INDEX idx_receipt_item_receipt ON inbound_receipt_items(receipt_id);
CREATE INDEX idx_receipt_item_po_item ON inbound_receipt_items(po_item_id);
