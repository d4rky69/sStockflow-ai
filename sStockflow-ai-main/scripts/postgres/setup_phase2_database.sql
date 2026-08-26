-- Run with psql as a PostgreSQL administrator:
-- psql -U postgres -f scripts/postgres/setup_phase2_database.sql

SELECT 'CREATE ROLE stockflow_app LOGIN PASSWORD ''stockflow_dev'''
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'stockflow_app')\gexec

SELECT 'CREATE DATABASE stockflow OWNER stockflow_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'stockflow')\gexec

\connect stockflow
GRANT CONNECT ON DATABASE stockflow TO stockflow_app;
GRANT USAGE, CREATE ON SCHEMA public TO stockflow_app;
