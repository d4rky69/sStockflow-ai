CREATE TABLE app_user (
    user_id VARCHAR(128) PRIMARY KEY,
    email VARCHAR(320),
    display_name VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_definition (
    role_code VARCHAR(64) PRIMARY KEY,
    role_name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE permission_definition (
    permission_code VARCHAR(100) PRIMARY KEY,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE role_permission (
    role_code VARCHAR(64) NOT NULL REFERENCES role_definition(role_code),
    permission_code VARCHAR(100) NOT NULL REFERENCES permission_definition(permission_code),
    PRIMARY KEY (role_code, permission_code)
);

CREATE TABLE tenant_membership (
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    user_id VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    role_code VARCHAR(64) NOT NULL REFERENCES role_definition(role_code),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id)
);

CREATE UNIQUE INDEX uq_warehouse_id_tenant ON warehouse(warehouse_id, tenant_id);

CREATE TABLE warehouse_access (
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (tenant_id, user_id, warehouse_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES tenant_membership(tenant_id, user_id),
    FOREIGN KEY (warehouse_id, tenant_id) REFERENCES warehouse(warehouse_id, tenant_id)
);

CREATE TABLE security_audit_event (
    audit_event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64),
    user_id VARCHAR(128),
    event_type VARCHAR(100) NOT NULL,
    resource VARCHAR(500),
    outcome VARCHAR(30) NOT NULL,
    details VARCHAR(2000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_membership_user_active ON tenant_membership(user_id, active);
CREATE INDEX idx_membership_tenant_role ON tenant_membership(tenant_id, role_code, active);
CREATE INDEX idx_security_audit_tenant_time ON security_audit_event(tenant_id, occurred_at DESC);

INSERT INTO role_definition(role_code, role_name, description) VALUES
('ADMIN', 'Administrator', 'Tenant administration and all operational permissions'),
('INVENTORY_MANAGER', 'Inventory Manager', 'Inventory, forecast and replenishment management'),
('WAREHOUSE_MANAGER', 'Warehouse Manager', 'Warehouse-scoped inventory and transfer operations'),
('PURCHASE_MANAGER', 'Purchase Manager', 'Purchase planning and supplier operations'),
('LOGISTICS_MANAGER', 'Logistics Manager', 'Transfers, routing and dispatch operations'),
('SUSTAINABILITY_ANALYST', 'Sustainability Analyst', 'Carbon and waste analytics'),
('APPROVER', 'Approver', 'Independent proposal approval'),
('VIEWER', 'Viewer', 'Read-only access');

INSERT INTO permission_definition(permission_code, description) VALUES
('INVENTORY_READ', 'Read warehouse, SKU and inventory data'),
('RISK_READ', 'Read inventory risks'),
('FORECAST_READ', 'Read forecasts and diagnostics'),
('FORECAST_RUN', 'Create forecast runs'),
('IMPORT_MANAGE', 'Upload and manage controlled imports'),
('TRANSFER_PROPOSE', 'Create transfer proposals'),
('PURCHASE_PROPOSE', 'Create purchase proposals'),
('PROPOSAL_APPROVE', 'Approve or reject proposals created by another user'),
('ROUTE_READ', 'Read route and vehicle recommendations'),
('SUSTAINABILITY_READ', 'Read carbon and waste analytics'),
('USER_MANAGE', 'Manage tenant users and roles');

INSERT INTO role_permission(role_code, permission_code)
SELECT 'ADMIN', permission_code FROM permission_definition;

INSERT INTO role_permission(role_code, permission_code) VALUES
('INVENTORY_MANAGER', 'INVENTORY_READ'), ('INVENTORY_MANAGER', 'RISK_READ'), ('INVENTORY_MANAGER', 'FORECAST_READ'), ('INVENTORY_MANAGER', 'FORECAST_RUN'), ('INVENTORY_MANAGER', 'TRANSFER_PROPOSE'), ('INVENTORY_MANAGER', 'PURCHASE_PROPOSE'),
('WAREHOUSE_MANAGER', 'INVENTORY_READ'), ('WAREHOUSE_MANAGER', 'RISK_READ'), ('WAREHOUSE_MANAGER', 'TRANSFER_PROPOSE'), ('WAREHOUSE_MANAGER', 'ROUTE_READ'),
('PURCHASE_MANAGER', 'INVENTORY_READ'), ('PURCHASE_MANAGER', 'RISK_READ'), ('PURCHASE_MANAGER', 'FORECAST_READ'), ('PURCHASE_MANAGER', 'PURCHASE_PROPOSE'),
('LOGISTICS_MANAGER', 'INVENTORY_READ'), ('LOGISTICS_MANAGER', 'RISK_READ'), ('LOGISTICS_MANAGER', 'TRANSFER_PROPOSE'), ('LOGISTICS_MANAGER', 'ROUTE_READ'), ('LOGISTICS_MANAGER', 'SUSTAINABILITY_READ'),
('SUSTAINABILITY_ANALYST', 'INVENTORY_READ'), ('SUSTAINABILITY_ANALYST', 'ROUTE_READ'), ('SUSTAINABILITY_ANALYST', 'SUSTAINABILITY_READ'),
('APPROVER', 'INVENTORY_READ'), ('APPROVER', 'RISK_READ'), ('APPROVER', 'FORECAST_READ'), ('APPROVER', 'PROPOSAL_APPROVE'),
('VIEWER', 'INVENTORY_READ'), ('VIEWER', 'RISK_READ'), ('VIEWER', 'FORECAST_READ'), ('VIEWER', 'ROUTE_READ'), ('VIEWER', 'SUSTAINABILITY_READ');
