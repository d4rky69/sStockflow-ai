CREATE TABLE product (
    product_id VARCHAR(80) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    vertical VARCHAR(50) NOT NULL,
    category VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT uq_product_tenant_name UNIQUE (tenant_id, product_name)
);

CREATE TABLE sku (
    sku_id VARCHAR(80) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(80) NOT NULL,
    sku_name VARCHAR(200) NOT NULL,
    base_uom VARCHAR(30) NOT NULL,
    unit_cost NUMERIC(19, 4) NOT NULL CHECK (unit_cost >= 0),
    selling_price NUMERIC(19, 4) NOT NULL CHECK (selling_price >= 0),
    currency VARCHAR(3) NOT NULL,
    minimum_safety_stock BIGINT NOT NULL CHECK (minimum_safety_stock >= 0),
    reorder_multiple BIGINT NOT NULL CHECK (reorder_multiple > 0),
    default_shelf_life_days INTEGER NOT NULL CHECK (default_shelf_life_days > 0),
    fefo_required BOOLEAN NOT NULL DEFAULT TRUE,
    demand_profile VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sku_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_sku_product
        FOREIGN KEY (product_id) REFERENCES product(product_id),
    CONSTRAINT uq_sku_tenant_name UNIQUE (tenant_id, sku_name)
);

CREATE INDEX idx_product_tenant ON product(tenant_id);
CREATE INDEX idx_sku_tenant ON sku(tenant_id);
CREATE INDEX idx_sku_tenant_active ON sku(tenant_id, active);
CREATE INDEX idx_sku_product ON sku(product_id);
