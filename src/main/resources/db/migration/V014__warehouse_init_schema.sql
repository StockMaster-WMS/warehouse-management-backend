-- ============================================================
-- warehouse-service: V1__init_schema.sql
-- Database: warehouse_db
-- ============================================================

-- TABLE: warehouses
CREATE TABLE warehouses (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(20)  UNIQUE NOT NULL,
    name        VARCHAR(150) NOT NULL,
    address     TEXT,
    timezone    VARCHAR(50)  DEFAULT 'Asia/Ho_Chi_Minh',
    is_active   BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMPTZ  DEFAULT now()
);

-- TABLE: locations
CREATE TABLE locations (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id    UUID           NOT NULL REFERENCES warehouses(id),
    code            VARCHAR(40)    NOT NULL,
    zone            VARCHAR(20)    NOT NULL,
    aisle           VARCHAR(10)    NOT NULL,
    rack            VARCHAR(10)    NOT NULL,
    level           SMALLINT       NOT NULL CHECK (level >= 1),
    bin             VARCHAR(10)    NOT NULL,
    location_type   VARCHAR(20)    DEFAULT 'STORAGE',
    max_weight_kg   NUMERIC(10,2),
    max_volume_cm3  NUMERIC(15,4),
    pick_sequence   INTEGER,
    status          VARCHAR(20)    DEFAULT 'AVAILABLE',
    is_active       BOOLEAN        DEFAULT TRUE,
    created_at      TIMESTAMPTZ    DEFAULT now(),
    CONSTRAINT uq_location_code UNIQUE (warehouse_id, code)
);
CREATE INDEX idx_locations_zone_status ON locations(warehouse_id, zone, status);

-- TABLE: stock_levels (tồn kho thực tế theo từng vị trí / lot)
CREATE TABLE stock_levels (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id  UUID         NOT NULL REFERENCES warehouses(id),
    location_id   UUID         NOT NULL REFERENCES locations(id),
    product_id    UUID         NOT NULL, -- ID từ product-service (cross-service reference)
    lot_number    VARCHAR(60)  DEFAULT '',
    expiry_date   DATE,
    qty_on_hand   INTEGER      NOT NULL CHECK (qty_on_hand >= 0),
    qty_reserved  INTEGER      DEFAULT 0 CHECK (qty_reserved >= 0),
    qty_available INTEGER      GENERATED ALWAYS AS (qty_on_hand - qty_reserved) STORED,
    updated_at    TIMESTAMPTZ  DEFAULT now(),
    CONSTRAINT uq_stock_level UNIQUE (location_id, product_id, lot_number)
);
CREATE INDEX idx_stock_product ON stock_levels(product_id);
