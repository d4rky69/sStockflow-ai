INSERT INTO permission_definition(permission_code, description)
VALUES ('ORDER_MANAGE', 'Create and advance tenant customer orders')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission(role_code, permission_code) VALUES
('ADMIN', 'ORDER_MANAGE'),
('INVENTORY_MANAGER', 'ORDER_MANAGE'),
('WAREHOUSE_MANAGER', 'ORDER_MANAGE')
ON CONFLICT DO NOTHING;

CREATE TABLE customer_order (
    order_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    order_number VARCHAR(40) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    customer_city VARCHAR(120) NOT NULL,
    channel VARCHAR(80) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL REFERENCES warehouse(warehouse_id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ALLOCATED','PICKING','READY_TO_SHIP','SHIPPED','ON_HOLD','CANCELLED')),
    promised_at TIMESTAMP NOT NULL,
    fulfilment_percent INTEGER NOT NULL DEFAULT 0 CHECK (fulfilment_percent BETWEEN 0 AND 100),
    total_value NUMERIC(19,4) NOT NULL CHECK (total_value >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    idempotency_key VARCHAR(160) NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_order_number UNIQUE (tenant_id, order_number),
    CONSTRAINT uq_customer_order_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE customer_order_line (
    line_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(order_id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    sku_id VARCHAR(80) NOT NULL REFERENCES sku(sku_id),
    ordered_quantity BIGINT NOT NULL CHECK (ordered_quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    line_value NUMERIC(19,4) NOT NULL CHECK (line_value >= 0)
);

CREATE TABLE customer_order_event (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(order_id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    comment VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_order_tenant_status ON customer_order(tenant_id, status, updated_at DESC);
CREATE INDEX idx_customer_order_warehouse ON customer_order(tenant_id, warehouse_id, promised_at);
CREATE INDEX idx_customer_order_line_order ON customer_order_line(order_id);
CREATE INDEX idx_customer_order_event_order ON customer_order_event(order_id, occurred_at);
