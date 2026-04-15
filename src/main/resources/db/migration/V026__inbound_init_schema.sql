-- ============================================================
-- inbound-service: V1__init_schema.sql
-- Database: inbound_db
-- ============================================================

-- TABLE: purchase_orders
CREATE TABLE purchase_orders (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number     VARCHAR(30)    UNIQUE NOT NULL,
    supplier_id   UUID           NOT NULL, -- ID từ product-service (cross-service reference)
    warehouse_id  UUID           NOT NULL, -- ID từ warehouse-service (cross-service reference)
    status        VARCHAR(20)    DEFAULT 'DRAFT',
    order_date    DATE           NOT NULL,
    expected_date DATE,
    total_amount  NUMERIC(18,4)  DEFAULT 0,
    created_at    TIMESTAMPTZ    DEFAULT now()
);

-- TABLE: po_items
CREATE TABLE po_items (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id         UUID           NOT NULL REFERENCES purchase_orders(id),
    line_number   SMALLINT       NOT NULL,
    product_id    UUID           NOT NULL,           -- cross-service reference
    product_sku   VARCHAR(50)    NOT NULL,           -- snapshot tại thời điểm tạo PO
    ordered_qty   INTEGER        NOT NULL CHECK (ordered_qty > 0),
    received_qty  INTEGER        DEFAULT 0,
    unit_price    NUMERIC(15,4),
    CONSTRAINT uq_po_line UNIQUE (po_id, line_number)
);

-- TABLE: putaway_tasks
CREATE TABLE putaway_tasks (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id           UUID        NOT NULL,  -- cross-service reference
    qty_to_putaway       INTEGER     NOT NULL,
    suggested_location_id UUID,                -- ID từ warehouse-service
    actual_location_id   UUID,                 -- ID từ warehouse-service
    status               VARCHAR(20) DEFAULT 'PENDING',
    assigned_to          UUID,
    completed_at         TIMESTAMPTZ
);
