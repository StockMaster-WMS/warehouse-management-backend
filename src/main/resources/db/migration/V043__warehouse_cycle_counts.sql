CREATE TABLE cycle_counts (
    id UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    scheduled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_by UUID,
    approved_by UUID,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cycle_count_items (
    id UUID PRIMARY KEY,
    cycle_count_id UUID NOT NULL REFERENCES cycle_counts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    location_id UUID NOT NULL,
    lot_number VARCHAR(50),
    system_qty INT NOT NULL,
    counted_qty INT,
    discrepancy INT,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    CONSTRAINT fk_cc_items_header FOREIGN KEY (cycle_count_id) REFERENCES cycle_counts(id)
);

CREATE INDEX idx_cc_warehouse ON cycle_counts(warehouse_id);
CREATE INDEX idx_cc_status ON cycle_counts(status);
CREATE INDEX idx_cc_items_header ON cycle_count_items(cycle_count_id);
