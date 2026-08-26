CREATE TABLE retailer (
    retailer_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    retailer_name VARCHAR(200) NOT NULL,
    retailer_type VARCHAR(50) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    city VARCHAR(120) NOT NULL,
    region VARCHAR(80) NOT NULL,
    credit_days INTEGER NOT NULL CHECK (credit_days >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_retailer_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_retailer_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(warehouse_id)
);

CREATE INDEX idx_retailer_tenant ON retailer(tenant_id);
CREATE INDEX idx_retailer_tenant_active ON retailer(tenant_id, active);
CREATE INDEX idx_retailer_warehouse ON retailer(warehouse_id);

CREATE TABLE sales_history (
    sales_history_id UUID PRIMARY KEY,
    sales_date DATE NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    retailer_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(80) NOT NULL,
    ordered_quantity BIGINT NOT NULL CHECK (ordered_quantity >= 0),
    fulfilled_quantity BIGINT NOT NULL CHECK (fulfilled_quantity >= 0),
    sales_quantity BIGINT NOT NULL CHECK (sales_quantity >= 0),
    return_quantity BIGINT NOT NULL DEFAULT 0 CHECK (return_quantity >= 0),
    lost_sales_quantity BIGINT NOT NULL DEFAULT 0 CHECK (lost_sales_quantity >= 0),
    unit_selling_price NUMERIC(19, 4) NOT NULL CHECK (unit_selling_price >= 0),
    promotion_id VARCHAR(80),
    stockout_flag BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_sales_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(warehouse_id),
    CONSTRAINT fk_sales_retailer FOREIGN KEY (retailer_id) REFERENCES retailer(retailer_id),
    CONSTRAINT fk_sales_sku FOREIGN KEY (sku_id) REFERENCES sku(sku_id),
    CONSTRAINT uq_sales_natural_key UNIQUE (tenant_id, sales_date, warehouse_id, retailer_id, sku_id)
);

CREATE INDEX idx_sales_tenant_date ON sales_history(tenant_id, sales_date);
CREATE INDEX idx_sales_tenant_sku_date ON sales_history(tenant_id, sku_id, sales_date);
CREATE INDEX idx_sales_tenant_warehouse_date ON sales_history(tenant_id, warehouse_id, sales_date);
CREATE INDEX idx_sales_tenant_stockout_date ON sales_history(tenant_id, stockout_flag, sales_date);
