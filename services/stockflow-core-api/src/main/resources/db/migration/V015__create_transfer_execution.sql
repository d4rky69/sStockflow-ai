INSERT INTO permission_definition(permission_code, description)
VALUES ('TRANSFER_EXECUTE', 'Reserve, dispatch and receive independently approved transfers')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission(role_code, permission_code) VALUES
('ADMIN', 'TRANSFER_EXECUTE'),
('LOGISTICS_MANAGER', 'TRANSFER_EXECUTE'),
('WAREHOUSE_MANAGER', 'TRANSFER_EXECUTE')
ON CONFLICT DO NOTHING;

CREATE TABLE transfer_execution (
    execution_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    proposal_id UUID NOT NULL REFERENCES action_proposal(proposal_id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PLANNED', 'RESERVED', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED')),
    sku_id VARCHAR(80) NOT NULL REFERENCES sku(sku_id),
    source_warehouse_id VARCHAR(64) NOT NULL REFERENCES warehouse(warehouse_id),
    destination_warehouse_id VARCHAR(64) NOT NULL REFERENCES warehouse(warehouse_id),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    route_reference VARCHAR(160),
    vehicle_reference VARCHAR(160),
    actual_transport_cost NUMERIC(19,4) CHECK (actual_transport_cost IS NULL OR actual_transport_cost >= 0),
    actual_carbon_kg NUMERIC(19,4) CHECK (actual_carbon_kg IS NULL OR actual_carbon_kg >= 0),
    idempotency_key VARCHAR(160) NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    dispatched_by VARCHAR(128) REFERENCES app_user(user_id),
    received_by VARCHAR(128) REFERENCES app_user(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reserved_at TIMESTAMP,
    dispatched_at TIMESTAMP,
    received_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_transfer_execution_proposal UNIQUE (tenant_id, proposal_id),
    CONSTRAINT uq_transfer_execution_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_transfer_execution_warehouses CHECK (source_warehouse_id <> destination_warehouse_id)
);

CREATE TABLE transfer_execution_allocation (
    allocation_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES transfer_execution(execution_id),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    source_batch_inventory_id UUID NOT NULL REFERENCES batch_inventory(batch_inventory_id),
    batch_number VARCHAR(100) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    expiry_date DATE NOT NULL,
    manufacture_date DATE,
    unit_cost NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    storage_condition_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_execution_source_batch UNIQUE (execution_id, source_batch_inventory_id)
);

CREATE TABLE transfer_execution_event (
    event_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES transfer_execution(execution_id),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    comment VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transfer_execution_tenant_status ON transfer_execution(tenant_id, status, updated_at DESC);
CREATE INDEX idx_transfer_allocation_execution ON transfer_execution_allocation(execution_id);
CREATE INDEX idx_transfer_event_execution_time ON transfer_execution_event(execution_id, occurred_at);
