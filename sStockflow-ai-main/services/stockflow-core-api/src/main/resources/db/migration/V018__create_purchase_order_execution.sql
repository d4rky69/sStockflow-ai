INSERT INTO permission_definition(permission_code, description)
VALUES ('PURCHASE_EXECUTE', 'Create, send, acknowledge and receive independently approved purchase orders')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission(role_code, permission_code) VALUES
('ADMIN', 'PURCHASE_EXECUTE'),
('PURCHASE_MANAGER', 'PURCHASE_EXECUTE'),
('WAREHOUSE_MANAGER', 'PURCHASE_EXECUTE')
ON CONFLICT DO NOTHING;

CREATE TABLE purchase_order (
    purchase_order_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    proposal_id UUID NOT NULL REFERENCES action_proposal(proposal_id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PO_CREATED','SENT_TO_SUPPLIER','ACKNOWLEDGED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    sku_id VARCHAR(80) NOT NULL REFERENCES sku(sku_id),
    destination_warehouse_id VARCHAR(64) NOT NULL REFERENCES warehouse(warehouse_id),
    supplier_reference VARCHAR(200) NOT NULL,
    ordered_quantity BIGINT NOT NULL CHECK (ordered_quantity > 0),
    received_quantity BIGINT NOT NULL DEFAULT 0 CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    currency VARCHAR(3) NOT NULL,
    expected_delivery_date DATE,
    supplier_acknowledgement_reference VARCHAR(200),
    idempotency_key VARCHAR(160) NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    sent_by VARCHAR(128) REFERENCES app_user(user_id),
    acknowledged_by VARCHAR(128) REFERENCES app_user(user_id),
    last_received_by VARCHAR(128) REFERENCES app_user(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    last_received_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_order_proposal UNIQUE (tenant_id, proposal_id),
    CONSTRAINT uq_purchase_order_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE purchase_order_receipt (
    receipt_id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_order(purchase_order_id),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    idempotency_key VARCHAR(160) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    batch_number VARCHAR(100) NOT NULL,
    manufacture_date DATE,
    expiry_date DATE NOT NULL,
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    storage_condition_code VARCHAR(40) NOT NULL,
    received_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_purchase_receipt_dates CHECK (manufacture_date IS NULL OR expiry_date > manufacture_date),
    CONSTRAINT uq_purchase_receipt_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE purchase_order_event (
    event_id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_order(purchase_order_id),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    comment VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchase_order_tenant_status ON purchase_order(tenant_id,status,updated_at DESC);
CREATE INDEX idx_purchase_order_open_supply ON purchase_order(tenant_id,destination_warehouse_id,sku_id,status);
CREATE INDEX idx_purchase_receipt_order ON purchase_order_receipt(purchase_order_id,received_at);
CREATE INDEX idx_purchase_event_order_time ON purchase_order_event(purchase_order_id,occurred_at);
