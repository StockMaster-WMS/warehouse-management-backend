-- ============================================================
-- outbound-service: V1__init_schema.sql
-- Database: outbound_db
-- ============================================================

-- TABLE: sales_orders
CREATE TABLE sales_orders (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    so_number        VARCHAR(30) UNIQUE NOT NULL,
    customer_name    VARCHAR(200) NOT NULL,
    shipping_address JSONB       NOT NULL,
    warehouse_id     UUID        NOT NULL, -- cross-service reference (warehouse-service)
    priority         SMALLINT    DEFAULT 5,
    status           VARCHAR(25) DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ DEFAULT now()
);

-- TABLE: picking_items
CREATE TABLE picking_items (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    so_item_id    UUID        NOT NULL,   -- liên kết logical với so_items (nếu có)
    product_id    UUID        NOT NULL,   -- cross-service reference (product-service)
    location_id   UUID        NOT NULL,   -- cross-service reference (warehouse-service)
    qty_to_pick   INTEGER     NOT NULL,
    qty_picked    INTEGER     DEFAULT 0,
    status        VARCHAR(20) DEFAULT 'PENDING',
    pick_sequence INTEGER
);
CREATE INDEX idx_pick_items_location ON picking_items(location_id);
