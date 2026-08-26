INSERT INTO tenant (tenant_id, tenant_name, vertical, currency, timezone, active)
VALUES ('TEN-ACME-PHARMA', 'Acme Pharma Distribution', 'PHARMA', 'INR', 'Asia/Kolkata', TRUE);

INSERT INTO warehouse (
    warehouse_id, tenant_id, warehouse_name, city, state, country,
    latitude, longitude, capacity_units, cold_chain_available, active
) VALUES
    ('WH-CHENNAI', 'TEN-ACME-PHARMA', 'Chennai Regional Warehouse', 'Chennai', 'Tamil Nadu', 'India', 13.0827000, 80.2707000, 280000, TRUE, TRUE),
    ('WH-BENGALURU', 'TEN-ACME-PHARMA', 'Bengaluru Regional Warehouse', 'Bengaluru', 'Karnataka', 'India', 12.9716000, 77.5946000, 380000, TRUE, TRUE),
    ('WH-HYDERABAD', 'TEN-ACME-PHARMA', 'Hyderabad Regional Warehouse', 'Hyderabad', 'Telangana', 'India', 17.3850000, 78.4867000, 470000, TRUE, TRUE);

INSERT INTO product (product_id, tenant_id, product_name, vertical, category, active)
VALUES ('PRD-PARA', 'TEN-ACME-PHARMA', 'Paracetamol', 'PHARMA', 'MEDICINE', TRUE);

INSERT INTO sku (
    sku_id, tenant_id, product_id, sku_name, base_uom, unit_cost,
    selling_price, currency, minimum_safety_stock, reorder_multiple,
    default_shelf_life_days, fefo_required, demand_profile, active
) VALUES (
    'SKU-PARA-650', 'TEN-ACME-PHARMA', 'PRD-PARA', 'Paracetamol 650mg Tablet',
    'UNIT', 18.5000, 25.0000, 'INR', 500, 100, 730, TRUE, 'STABLE', TRUE
);

INSERT INTO batch_inventory (
    batch_inventory_id, snapshot_date, tenant_id, warehouse_id, sku_id,
    batch_number, manufacture_date, expiry_date, available_quantity,
    reserved_quantity, blocked_quantity, unit_cost, currency,
    storage_condition_code, last_movement_at
) VALUES
    ('11111111-1111-1111-1111-111111111111', DATE '2026-07-26', 'TEN-ACME-PHARMA', 'WH-CHENNAI', 'SKU-PARA-650',
     'B2456', DATE '2026-01-27', DATE '2026-09-09', 2450, 59, 0, 18.5000, 'INR', 'AMBIENT', TIMESTAMP '2026-07-15 00:00:00'),
    ('22222222-2222-2222-2222-222222222222', DATE '2026-07-26', 'TEN-ACME-PHARMA', 'WH-BENGALURU', 'SKU-PARA-650',
     'B9355', DATE '2026-01-27', DATE '2028-01-04', 2346, 37, 0, 18.5000, 'INR', 'AMBIENT', TIMESTAMP '2026-07-21 00:00:00'),
    ('33333333-3333-3333-3333-333333333333', DATE '2026-07-26', 'TEN-ACME-PHARMA', 'WH-HYDERABAD', 'SKU-PARA-650',
     'B6868', DATE '2026-01-27', DATE '2027-11-29', 2655, 61, 0, 18.5000, 'INR', 'AMBIENT', TIMESTAMP '2026-07-06 00:00:00');
