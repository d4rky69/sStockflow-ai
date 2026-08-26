CREATE TABLE district_registry (
    district_id VARCHAR(50) PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(tenant_id),
    name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    source VARCHAR(100) NOT NULL,
    extracted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    confidence_score NUMERIC(4,3) NOT NULL,
    geometry_json TEXT NOT NULL
);

CREATE INDEX idx_district_tenant ON district_registry(tenant_id);
