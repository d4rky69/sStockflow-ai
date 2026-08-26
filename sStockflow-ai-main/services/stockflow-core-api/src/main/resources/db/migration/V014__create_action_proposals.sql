CREATE TABLE action_proposal (
    proposal_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    proposal_type VARCHAR(30) NOT NULL CHECK (proposal_type IN ('TRANSFER', 'PURCHASE')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')),
    sku_id VARCHAR(80) NOT NULL REFERENCES sku(sku_id),
    quantity NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    source_warehouse_id VARCHAR(64) REFERENCES warehouse(warehouse_id),
    destination_warehouse_id VARCHAR(64) REFERENCES warehouse(warehouse_id),
    supplier_reference VARCHAR(200),
    unit_cost NUMERIC(19, 4) CHECK (unit_cost IS NULL OR unit_cost >= 0),
    transport_cost NUMERIC(19, 4) CHECK (transport_cost IS NULL OR transport_cost >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    reason VARCHAR(1000) NOT NULL,
    recommendation_evidence TEXT,
    idempotency_key VARCHAR(160) NOT NULL,
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    submitted_by VARCHAR(128) REFERENCES app_user(user_id),
    reviewed_by VARCHAR(128) REFERENCES app_user(user_id),
    review_comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    reviewed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_action_proposal_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_transfer_fields CHECK (
        proposal_type <> 'TRANSFER' OR (
            source_warehouse_id IS NOT NULL AND destination_warehouse_id IS NOT NULL
            AND source_warehouse_id <> destination_warehouse_id
        )
    ),
    CONSTRAINT ck_purchase_fields CHECK (
        proposal_type <> 'PURCHASE' OR destination_warehouse_id IS NOT NULL
    )
);

CREATE TABLE proposal_status_history (
    history_id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL REFERENCES action_proposal(proposal_id),
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    changed_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    comment VARCHAR(1000),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_action_proposal_tenant_status ON action_proposal(tenant_id, status, updated_at DESC);
CREATE INDEX idx_action_proposal_duplicate_transfer ON action_proposal(tenant_id, proposal_type, sku_id, source_warehouse_id, destination_warehouse_id, status);
CREATE INDEX idx_action_proposal_duplicate_purchase ON action_proposal(tenant_id, proposal_type, sku_id, destination_warehouse_id, supplier_reference, status);
CREATE INDEX idx_proposal_history_proposal_time ON proposal_status_history(proposal_id, changed_at);
