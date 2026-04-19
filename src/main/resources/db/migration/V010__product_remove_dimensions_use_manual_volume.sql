ALTER TABLE products
    ADD COLUMN volume_cm3_manual NUMERIC(12,4);

UPDATE products
SET volume_cm3_manual = volume_cm3
WHERE volume_cm3_manual IS NULL;

ALTER TABLE products
    DROP COLUMN IF EXISTS volume_cm3,
    DROP COLUMN IF EXISTS length_cm,
    DROP COLUMN IF EXISTS width_cm,
    DROP COLUMN IF EXISTS height_cm;

ALTER TABLE products
    RENAME COLUMN volume_cm3_manual TO volume_cm3;

ALTER TABLE products
    ADD CONSTRAINT chk_products_volume_cm3_non_negative CHECK (volume_cm3 IS NULL OR volume_cm3 >= 0);
