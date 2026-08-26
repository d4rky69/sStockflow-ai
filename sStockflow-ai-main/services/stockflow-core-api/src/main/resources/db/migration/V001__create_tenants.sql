CREATE TABLE tenant (
    tenant_id VARCHAR(64) PRIMARY KEY,
    tenant_name VARCHAR(200) NOT NULL,
    vertical VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tenant_active ON tenant(active);
