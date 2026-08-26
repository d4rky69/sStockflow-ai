CREATE INDEX idx_batch_tenant_snapshot_warehouse_sku
    ON batch_inventory(tenant_id, snapshot_date, warehouse_id, sku_id);

CREATE INDEX idx_sales_tenant_date_warehouse_sku
    ON sales_history(tenant_id, sales_date, warehouse_id, sku_id);
