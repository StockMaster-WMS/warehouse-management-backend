-- ============================================================
-- warehouse-service: V6__remove_duplicate_warehouse_w2.sql
-- Remove duplicate demo warehouse W-2 and related data
-- ============================================================

WITH target_warehouse AS (
    SELECT id
    FROM warehouses
    WHERE code = 'W-2'
)
DELETE FROM stock_levels sl
USING target_warehouse tw
WHERE sl.warehouse_id = tw.id;

WITH target_warehouse AS (
    SELECT id
    FROM warehouses
    WHERE code = 'W-2'
)
DELETE FROM locations l
USING target_warehouse tw
WHERE l.warehouse_id = tw.id;

DELETE FROM warehouses
WHERE code = 'W-2';
