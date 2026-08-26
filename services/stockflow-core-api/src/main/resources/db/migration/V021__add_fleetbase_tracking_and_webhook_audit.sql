ALTER TABLE fleetbase_order_link ADD COLUMN remote_status VARCHAR(60);
ALTER TABLE fleetbase_order_link ADD COLUMN tracking_number VARCHAR(160);
ALTER TABLE fleetbase_order_link ADD COLUMN progress_percentage NUMERIC(7,3);
ALTER TABLE fleetbase_order_link ADD COLUMN eta_seconds BIGINT;
ALTER TABLE fleetbase_order_link ADD COLUMN latitude NUMERIC(10,7);
ALTER TABLE fleetbase_order_link ADD COLUMN longitude NUMERIC(10,7);
ALTER TABLE fleetbase_order_link ADD COLUMN last_tracker_at TIMESTAMP;
ALTER TABLE fleetbase_order_link ADD COLUMN last_reconciled_at TIMESTAMP;
ALTER TABLE fleetbase_order_link ADD COLUMN last_webhook_at TIMESTAMP;
ALTER TABLE fleetbase_order_link ADD COLUMN reconciliation_status VARCHAR(40) NOT NULL DEFAULT 'NOT_CHECKED';

CREATE TABLE fleetbase_webhook_event (
    event_id VARCHAR(160) PRIMARY KEY,
    event_name VARCHAR(120) NOT NULL,
    fleetbase_order_id VARCHAR(160),
    tenant_id VARCHAR(64) REFERENCES tenant(tenant_id),
    link_id UUID REFERENCES fleetbase_order_link(link_id),
    payload_hash VARCHAR(64) NOT NULL,
    processing_status VARCHAR(30) NOT NULL CHECK (processing_status IN ('APPLIED', 'IGNORED')),
    remote_status VARCHAR(60),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fleetbase_webhook_order ON fleetbase_webhook_event(fleetbase_order_id, received_at DESC);
CREATE INDEX idx_fleetbase_webhook_tenant ON fleetbase_webhook_event(tenant_id, received_at DESC);
