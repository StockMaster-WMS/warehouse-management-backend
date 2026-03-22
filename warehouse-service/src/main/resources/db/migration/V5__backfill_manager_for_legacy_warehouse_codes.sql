-- ============================================================
-- warehouse-service: V5__backfill_manager_for_legacy_warehouse_codes.sql
-- Backfill manager_name for legacy/demo warehouse codes (W-1, W-2)
-- ============================================================

UPDATE warehouses
SET
    manager_name = CASE
        WHEN code = 'W-1' THEN 'Nguyen Van A'
        WHEN code = 'W-2' THEN 'Tran Thi B'
        WHEN name ILIKE 'Kho 1%' AND code = 'W-1' THEN 'Nguyen Van A'
        WHEN name ILIKE 'Kho 1%' AND code = 'W-2' THEN 'Tran Thi B'
        ELSE manager_name
    END,
    updated_at = COALESCE(updated_at, now())
WHERE manager_name IS NULL
  AND (
      code IN ('W-1', 'W-2')
      OR (name ILIKE 'Kho 1%' AND code IN ('W-1', 'W-2'))
  );
