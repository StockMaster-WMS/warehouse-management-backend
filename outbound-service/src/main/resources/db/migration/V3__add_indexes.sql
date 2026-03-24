-- Indexes for SalesOrder list filters and default sort
CREATE INDEX idx_so_status       ON sales_orders(status);
CREATE INDEX idx_so_warehouse    ON sales_orders(warehouse_id);
CREATE INDEX idx_so_created_at   ON sales_orders(created_at DESC);

-- Index for PickingItem FK (used heavily in markShipped loop and spec filter)
CREATE INDEX idx_pick_so_item    ON picking_items(so_item_id);
