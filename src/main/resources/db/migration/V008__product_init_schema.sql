-- ============================================================
-- product-service: V1__init_schema.sql
-- Database: product_db
-- ============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- TABLE: categories
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(30)  UNIQUE NOT NULL,
    name        VARCHAR(120) NOT NULL,
    parent_id   UUID         REFERENCES categories(id),
    path        VARCHAR(500),
    level       SMALLINT     DEFAULT 0,
    is_active   BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMPTZ  DEFAULT now()
);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_path      ON categories USING GIN (path gin_trgm_ops);

-- TABLE: suppliers
CREATE TABLE suppliers (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(20)  UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    tax_code        VARCHAR(20)  UNIQUE,
    contact_name    VARCHAR(100),
    contact_phone   VARCHAR(20),
    contact_email   VARCHAR(100),
    address         TEXT,
    payment_terms   SMALLINT     DEFAULT 30,
    lead_time_days  SMALLINT     DEFAULT 7,
    status          VARCHAR(20)  DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  DEFAULT now(),
    updated_at      TIMESTAMPTZ  DEFAULT now()
);

-- TABLE: products
CREATE TABLE products (
    id                  UUID           PRIMARY KEY,
    sku                 VARCHAR(50)    UNIQUE NOT NULL,
    barcode_ean13       VARCHAR(13)    UNIQUE,
    name                VARCHAR(255)   NOT NULL,
    category_id         UUID           NOT NULL REFERENCES categories(id),
    primary_supplier_id UUID           REFERENCES suppliers(id),
    base_unit           VARCHAR(20)    NOT NULL,
    weight_kg           NUMERIC(10,4)  CHECK (weight_kg >= 0),
    length_cm           NUMERIC(8,2),
    width_cm            NUMERIC(8,2),
    height_cm           NUMERIC(8,2),
    volume_cm3          NUMERIC(12,4)  GENERATED ALWAYS AS (length_cm * width_cm * height_cm) STORED,
    min_stock_qty       INTEGER        DEFAULT 0,
    is_lot_tracked      BOOLEAN        DEFAULT FALSE,
    is_expiry_tracked   BOOLEAN        DEFAULT FALSE,
    status              VARCHAR(20)    DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ    DEFAULT now(),
    updated_at          TIMESTAMPTZ    DEFAULT now(),
    created_by          UUID           NOT NULL
);
CREATE INDEX idx_products_sku      ON products(sku);
CREATE INDEX idx_products_category ON products(category_id);
