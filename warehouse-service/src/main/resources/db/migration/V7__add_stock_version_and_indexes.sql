-- Optimistic locking for stock adjustments
ALTER TABLE stock_levels ADD COLUMN version BIGINT DEFAULT 0;

-- Indexes for common filter paths (StockLevelSpecification)
CREATE INDEX idx_stock_warehouse ON stock_levels(warehouse_id);
CREATE INDEX idx_stock_location  ON stock_levels(location_id);
