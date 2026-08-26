CREATE TABLE batch_inventory (
    batch_inventory_id UUID PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(80) NOT NULL,
    batch_number VARCHAR(100) NOT NULL,
    manufacture_date DATE,
    expiry_date DATE NOT NULL,
    available_quantity BIGINT NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity BIGINT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    blocked_quantity BIGINT NOT NULL DEFAULT 0 CHECK (blocked_quantity >= 0),
    unit_cost NUMERIC(19, 4) NOT NULL CHECK (unit_cost >= 0),
    currency VARCHAR(3) NOT NULL,
    storage_condition_code VARCHAR(40) NOT NULL,
    last_movement_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_batch_inventory_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_batch_inventory_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_batch_inventory_sku
        FOREIGN KEY (sku_id) REFERENCES sku(sku_id),
    CONSTRAINT ck_batch_inventory_dates
        CHECK (manufacture_date IS NULL OR expiry_date > manufacture_date),
    CONSTRAINT ck_batch_inventory_allocations
        CHECK (reserved_quantity + blocked_quantity <= available_quantity),
    CONSTRAINT uq_batch_inventory_snapshot
        UNIQUE (snapshot_date, tenant_id, warehouse_id, sku_id, batch_number)
);

CREATE INDEX idx_batch_inventory_tenant ON batch_inventory(tenant_id);
CREATE INDEX idx_batch_inventory_tenant_warehouse ON batch_inventory(tenant_id, warehouse_id);
CREATE INDEX idx_batch_inventory_tenant_sku ON batch_inventory(tenant_id, sku_id);
CREATE INDEX idx_batch_inventory_expiry ON batch_inventory(tenant_id, expiry_date);
