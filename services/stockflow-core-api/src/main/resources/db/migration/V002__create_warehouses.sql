CREATE TABLE warehouse (
    warehouse_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    warehouse_name VARCHAR(200) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'India',
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    capacity_units BIGINT NOT NULL CHECK (capacity_units >= 0),
    cold_chain_available BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_warehouse_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id)
);

CREATE INDEX idx_warehouse_tenant ON warehouse(tenant_id);
CREATE INDEX idx_warehouse_tenant_active ON warehouse(tenant_id, active);
