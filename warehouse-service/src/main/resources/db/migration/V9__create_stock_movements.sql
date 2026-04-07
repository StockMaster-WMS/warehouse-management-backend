-- Bảng lịch sử biến động tồn kho
CREATE TABLE IF NOT EXISTS stock_movements (
    id              UUID        PRIMARY KEY,
    warehouse_id    UUID        NOT NULL REFERENCES warehouses(id),
    location_id     UUID        NOT NULL REFERENCES locations(id),
    product_id      UUID        NOT NULL,
    lot_number      VARCHAR(60) NOT NULL DEFAULT '',

    movement_type   VARCHAR(30) NOT NULL,       -- INBOUND, OUTBOUND, ADJUSTMENT, RESERVE, RELEASE
    qty_change      INTEGER     NOT NULL,       -- Biến động (+/-)
    qty_after       INTEGER     NOT NULL,       -- Số lượng sau biến động
    reserved_change INTEGER     NOT NULL DEFAULT 0,  -- Biến động reserved (+/-)
    reserved_after  INTEGER     NOT NULL DEFAULT 0,  -- Reserved sau biến động

    reason          VARCHAR(255),               -- Lý do / ghi chú
    reference_type  VARCHAR(60),                -- Loại tham chiếu: PURCHASE_ORDER, SALES_ORDER, MANUAL
    reference_id    UUID,                       -- ID tham chiếu (PO, SO, ...)

    created_by      VARCHAR(120),               -- Người thực hiện
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sm_warehouse  ON stock_movements(warehouse_id);
CREATE INDEX idx_sm_product    ON stock_movements(product_id);
CREATE INDEX idx_sm_location   ON stock_movements(location_id);
CREATE INDEX idx_sm_created_at ON stock_movements(created_at);
CREATE INDEX idx_sm_type       ON stock_movements(movement_type);
