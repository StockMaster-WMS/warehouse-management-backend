-- Dòng đơn xuất + khóa ngoại picking -> dòng đơn
CREATE TABLE sales_order_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id   UUID        NOT NULL REFERENCES sales_orders (id) ON DELETE CASCADE,
    line_number      SMALLINT    NOT NULL,
    product_id       UUID        NOT NULL,
    product_sku      VARCHAR(50) NOT NULL,
    ordered_qty      INTEGER     NOT NULL CHECK (ordered_qty > 0),
    shipped_qty      INTEGER     NOT NULL DEFAULT 0 CHECK (shipped_qty >= 0),
    unit_price       NUMERIC(15, 4),
    CONSTRAINT uq_so_line UNIQUE (sales_order_id, line_number)
);

CREATE INDEX idx_so_items_order ON sales_order_items (sales_order_id);

ALTER TABLE picking_items
    ADD CONSTRAINT fk_picking_so_item FOREIGN KEY (so_item_id) REFERENCES sales_order_items (id);
