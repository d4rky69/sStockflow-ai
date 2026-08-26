-- H2 requires a UNIQUE constraint, rather than only a unique index,
-- for the composite warehouse foreign key created by migration V013.
-- PostgreSQL profiles do not include this H2-only Flyway location.
ALTER TABLE warehouse
    ADD CONSTRAINT uq_warehouse_id_tenant_h2 UNIQUE (warehouse_id, tenant_id);
