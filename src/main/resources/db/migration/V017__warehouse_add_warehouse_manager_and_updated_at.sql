-- ============================================================
-- warehouse-service: V4__add_warehouse_manager_and_updated_at.sql
-- Add manager name and updated timestamp for richer warehouse card data
-- ============================================================

ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS manager_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE warehouses
SET updated_at = COALESCE(updated_at, created_at, now());

UPDATE warehouses
SET manager_name = CASE code
    WHEN 'WH-HCM-DC01' THEN 'Nguyen Van A'
    WHEN 'WH-HCM-CFS02' THEN 'Tran Thi B'
    WHEN 'WH-BD-DC01' THEN 'Le Quoc C'
    WHEN 'WH-DN-DC01' THEN 'Pham Thi D'
    WHEN 'WH-HN-DC01' THEN 'Hoang Van E'
    WHEN 'WH-HP-PORT01' THEN 'Bui Minh F'
    WHEN 'WH-CT-R01' THEN 'Dang Thi G'
    WHEN 'WH-HCM-RET01' THEN 'Doan Van H'
    ELSE manager_name
END
WHERE manager_name IS NULL;
