CREATE TABLE rma_headers (
    id UUID PRIMARY KEY,
    rma_number VARCHAR(30) UNIQUE NOT NULL,
    sales_order_id UUID REFERENCES sales_orders(id),
    customer_name VARCHAR(200),
    status VARCHAR(25) NOT NULL DEFAULT 'REQUESTED',
    reason TEXT,
    warehouse_id UUID NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE TABLE rma_items (
    id UUID PRIMARY KEY,
    rma_id UUID NOT NULL REFERENCES rma_headers(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    expected_qty INT NOT NULL,
    received_qty INT DEFAULT 0,
    lot_number VARCHAR(50),
    condition VARCHAR(50),
    notes TEXT
);

CREATE INDEX idx_rma_number ON rma_headers(rma_number);
CREATE INDEX idx_rma_status ON rma_headers(status);
CREATE INDEX idx_rma_items_header ON rma_items(rma_id);
