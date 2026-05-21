CREATE TABLE IF NOT EXISTS user_warehouses (
    user_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, warehouse_id),
    CONSTRAINT fk_user_warehouses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_warehouses_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_warehouses_user_id ON user_warehouses(user_id);
CREATE INDEX IF NOT EXISTS idx_user_warehouses_warehouse_id ON user_warehouses(warehouse_id);

INSERT INTO user_warehouses (user_id, warehouse_id)
SELECT wm.user_id, wm.warehouse_id
FROM warehouse_managers wm
ON CONFLICT (user_id, warehouse_id) DO NOTHING;
