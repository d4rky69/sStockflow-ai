CREATE TABLE fleetbase_order_link (
    link_id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenant(tenant_id),
    transfer_execution_id UUID NOT NULL REFERENCES transfer_execution(execution_id),
    proposal_id UUID NOT NULL REFERENCES action_proposal(proposal_id),
    fleetbase_organization_id VARCHAR(160) NOT NULL,
    fleetbase_order_id VARCHAR(160),
    fleetbase_internal_id VARCHAR(160) NOT NULL,
    vehicle_id VARCHAR(160),
    link_status VARCHAR(30) NOT NULL CHECK (link_status IN ('PREPARED', 'CREATED', 'DISPATCHED', 'FAILED', 'CANCELLED')),
    idempotency_key VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error_code VARCHAR(100),
    last_error_message VARCHAR(1000),
    created_by VARCHAR(128) NOT NULL REFERENCES app_user(user_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remote_created_at TIMESTAMP,
    dispatched_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fleetbase_link_execution UNIQUE (tenant_id, transfer_execution_id),
    CONSTRAINT uq_fleetbase_link_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_fleetbase_link_remote_order UNIQUE (fleetbase_organization_id, fleetbase_order_id)
);

CREATE INDEX idx_fleetbase_link_tenant_status ON fleetbase_order_link(tenant_id, link_status, updated_at DESC);
CREATE INDEX idx_fleetbase_link_proposal ON fleetbase_order_link(tenant_id, proposal_id);
