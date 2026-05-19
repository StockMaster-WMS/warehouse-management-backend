DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_stock_reserved_not_exceed_on_hand'
    ) THEN
        ALTER TABLE stock_levels
            ADD CONSTRAINT chk_stock_reserved_not_exceed_on_hand
            CHECK (qty_reserved <= qty_on_hand) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_locations_id_warehouse'
    ) THEN
        ALTER TABLE locations
            ADD CONSTRAINT uq_locations_id_warehouse UNIQUE (id, warehouse_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_stock_levels_location_warehouse'
    ) THEN
        ALTER TABLE stock_levels
            ADD CONSTRAINT fk_stock_levels_location_warehouse
            FOREIGN KEY (location_id, warehouse_id)
            REFERENCES locations(id, warehouse_id) NOT VALID;
    END IF;
END $$;