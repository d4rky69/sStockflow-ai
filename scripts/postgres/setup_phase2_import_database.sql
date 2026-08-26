-- Run with psql as a PostgreSQL administrator on the configured server port.
-- Example:
-- psql -h localhost -p 5433 -U postgres -d postgres -f scripts/postgres/setup_phase2_import_database.sql

SELECT 'CREATE ROLE stockflow_app LOGIN PASSWORD ''stockflow_dev'''
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'stockflow_app')\gexec

SELECT 'CREATE DATABASE stockflow_phase2 OWNER stockflow_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'stockflow_phase2')\gexec

\connect stockflow_phase2
GRANT CONNECT ON DATABASE stockflow_phase2 TO stockflow_app;
GRANT USAGE, CREATE ON SCHEMA public TO stockflow_app;
