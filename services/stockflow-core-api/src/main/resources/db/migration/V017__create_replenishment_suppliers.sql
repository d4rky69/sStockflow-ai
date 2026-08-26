CREATE TABLE supplier (
    supplier_id VARCHAR(80) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    supplier_name VARCHAR(200) NOT NULL,
    lead_time_days INTEGER NOT NULL CHECK (lead_time_days BETWEEN 1 AND 365),
    minimum_order_value NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (minimum_order_value >= 0),
    on_time_in_full_percent NUMERIC(5, 2) NOT NULL DEFAULT 90 CHECK (on_time_in_full_percent BETWEEN 0 AND 100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_supplier_tenant_name UNIQUE (tenant_id, supplier_name)
);

CREATE TABLE sku_supplier (
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    sku_id VARCHAR(80) NOT NULL REFERENCES sku(sku_id),
    supplier_id VARCHAR(80) NOT NULL REFERENCES supplier(supplier_id),
    supplier_unit_cost NUMERIC(19, 4) NOT NULL CHECK (supplier_unit_cost >= 0),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, sku_id, supplier_id)
);

CREATE INDEX idx_supplier_tenant_active ON supplier(tenant_id, active);
CREATE INDEX idx_sku_supplier_preferred ON sku_supplier(tenant_id, sku_id, preferred, active);

INSERT INTO supplier(supplier_id, tenant_id, supplier_name, lead_time_days, on_time_in_full_percent)
SELECT values.supplier_id, values.tenant_id, values.supplier_name, values.lead_time_days, values.otif
FROM (VALUES
    ('SUP-APEX', 'TEN-ACME-PHARMA', 'Apex Remedies', 5, 91.00),
    ('SUP-NOVACURE', 'TEN-ACME-PHARMA', 'NovaCure Labs', 6, 94.00),
    ('SUP-WELLSPRING', 'TEN-ACME-PHARMA', 'WellSpring Pharma', 7, 93.00),
    ('SUP-MEDAXIS', 'TEN-ACME-PHARMA', 'MedAxis Biologics', 4, 98.00)
) AS values(supplier_id, tenant_id, supplier_name, lead_time_days, otif)
WHERE EXISTS (SELECT 1 FROM tenant t WHERE t.tenant_id = values.tenant_id)
ON CONFLICT DO NOTHING;

INSERT INTO sku_supplier(tenant_id, sku_id, supplier_id, supplier_unit_cost, preferred)
SELECT values.tenant_id, values.sku_id, values.supplier_id, s.unit_cost, TRUE
FROM (VALUES
    ('TEN-ACME-PHARMA', 'SKU-PARA-650', 'SUP-APEX'),
    ('TEN-ACME-PHARMA', 'SKU-AMOX-500', 'SUP-NOVACURE'),
    ('TEN-ACME-PHARMA', 'SKU-ORS-21', 'SUP-WELLSPRING'),
    ('TEN-ACME-PHARMA', 'SKU-INS-GLR', 'SUP-MEDAXIS')
) AS values(tenant_id, sku_id, supplier_id)
JOIN sku s ON s.tenant_id = values.tenant_id AND s.sku_id = values.sku_id
JOIN supplier p ON p.tenant_id = values.tenant_id AND p.supplier_id = values.supplier_id
ON CONFLICT DO NOTHING;
