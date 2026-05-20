CREATE TABLE IF NOT EXISTS warehouse_managers (
    warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (warehouse_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_warehouse_managers_user ON warehouse_managers(user_id);
CREATE INDEX IF NOT EXISTS idx_warehouse_managers_warehouse ON warehouse_managers(warehouse_id);
