-- ============================================================
-- outbound-service: V5__customers.sql
-- Bảng master khách hàng dùng cho outbound-service
-- ============================================================

CREATE TABLE customers (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(30) UNIQUE NOT NULL,
    name          VARCHAR(200) NOT NULL,
    contact_name  VARCHAR(120),
    phone         VARCHAR(20),
    email         VARCHAR(120),
    tax_code      VARCHAR(50),
    address       JSONB,
    notes         VARCHAR(500),
    is_active     BOOLEAN     DEFAULT true,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_customers_name ON customers(name);
CREATE INDEX idx_customers_is_active ON customers(is_active);
