-- Dedicated return locations per active warehouse.
-- RMA_DAMAGED: hàng lỗi/hư hỏng
-- RMA_EXPIRED: hàng hết hạn/quá hạn
-- RMA_QUARANTINE: hàng chờ kiểm định/chưa rõ tình trạng
-- RMA_RESTOCK: hàng tốt có thể nhập lại tồn sau kiểm tra

INSERT INTO locations (
    id, warehouse_id, code, zone, aisle, rack, level, bin, location_type,
    max_weight_kg, max_volume_cm3, pick_sequence, status, is_active,
    is_cold_zone, is_hazmat_zone, is_heavy_zone, created_at
)
SELECT gen_random_uuid(), w.id, 'RMA-DMG-01', 'RMA', 'DMG', '01', 1, '01', 'RMA_DAMAGED',
       1000.00, 200000.0000, 900, 'AVAILABLE', TRUE,
       FALSE, FALSE, FALSE, now()
FROM warehouses w
WHERE w.is_active = TRUE
ON CONFLICT (warehouse_id, code) DO UPDATE
SET zone = EXCLUDED.zone,
    aisle = EXCLUDED.aisle,
    rack = EXCLUDED.rack,
    level = EXCLUDED.level,
    bin = EXCLUDED.bin,
    location_type = EXCLUDED.location_type,
    status = EXCLUDED.status,
    is_active = EXCLUDED.is_active;

INSERT INTO locations (
    id, warehouse_id, code, zone, aisle, rack, level, bin, location_type,
    max_weight_kg, max_volume_cm3, pick_sequence, status, is_active,
    is_cold_zone, is_hazmat_zone, is_heavy_zone, created_at
)
SELECT gen_random_uuid(), w.id, 'RMA-EXP-01', 'RMA', 'EXP', '01', 1, '01', 'RMA_EXPIRED',
       1000.00, 200000.0000, 910, 'AVAILABLE', TRUE,
       FALSE, FALSE, FALSE, now()
FROM warehouses w
WHERE w.is_active = TRUE
ON CONFLICT (warehouse_id, code) DO UPDATE
SET zone = EXCLUDED.zone,
    aisle = EXCLUDED.aisle,
    rack = EXCLUDED.rack,
    level = EXCLUDED.level,
    bin = EXCLUDED.bin,
    location_type = EXCLUDED.location_type,
    status = EXCLUDED.status,
    is_active = EXCLUDED.is_active;

INSERT INTO locations (
    id, warehouse_id, code, zone, aisle, rack, level, bin, location_type,
    max_weight_kg, max_volume_cm3, pick_sequence, status, is_active,
    is_cold_zone, is_hazmat_zone, is_heavy_zone, created_at
)
SELECT gen_random_uuid(), w.id, 'RMA-QRT-01', 'RMA', 'QRT', '01', 1, '01', 'RMA_QUARANTINE',
       1000.00, 200000.0000, 920, 'AVAILABLE', TRUE,
       FALSE, FALSE, FALSE, now()
FROM warehouses w
WHERE w.is_active = TRUE
ON CONFLICT (warehouse_id, code) DO UPDATE
SET zone = EXCLUDED.zone,
    aisle = EXCLUDED.aisle,
    rack = EXCLUDED.rack,
    level = EXCLUDED.level,
    bin = EXCLUDED.bin,
    location_type = EXCLUDED.location_type,
    status = EXCLUDED.status,
    is_active = EXCLUDED.is_active;

INSERT INTO locations (
    id, warehouse_id, code, zone, aisle, rack, level, bin, location_type,
    max_weight_kg, max_volume_cm3, pick_sequence, status, is_active,
    is_cold_zone, is_hazmat_zone, is_heavy_zone, created_at
)
SELECT gen_random_uuid(), w.id, 'RMA-RST-01', 'RMA', 'RST', '01', 1, '01', 'RMA_RESTOCK',
       1000.00, 200000.0000, 930, 'AVAILABLE', TRUE,
       FALSE, FALSE, FALSE, now()
FROM warehouses w
WHERE w.is_active = TRUE
ON CONFLICT (warehouse_id, code) DO UPDATE
SET zone = EXCLUDED.zone,
    aisle = EXCLUDED.aisle,
    rack = EXCLUDED.rack,
    level = EXCLUDED.level,
    bin = EXCLUDED.bin,
    location_type = EXCLUDED.location_type,
    status = EXCLUDED.status,
    is_active = EXCLUDED.is_active;
