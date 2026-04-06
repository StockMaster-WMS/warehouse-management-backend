ALTER TABLE products
    ADD COLUMN IF NOT EXISTS volume_cm3 NUMERIC(12,4),
    ADD COLUMN IF NOT EXISTS is_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_fragile BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_hazmat BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS is_heavy BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_products_volume_cm3_non_negative'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_volume_cm3_non_negative
            CHECK (volume_cm3 IS NULL OR volume_cm3 >= 0);
    END IF;
END $$;
