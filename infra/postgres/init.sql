-- ============================================================
-- Supply Chain Platform — PostgreSQL Initialization
-- Creates one schema per tenant + shared auth schema
-- ============================================================

-- Tenant schemas (one per client company)
CREATE SCHEMA IF NOT EXISTS toyota;
CREATE SCHEMA IF NOT EXISTS honda;
CREATE SCHEMA IF NOT EXISTS mitsubishi;
CREATE SCHEMA IF NOT EXISTS daihatsu;

-- Shared schema for auth users table (used by API Gateway)
CREATE SCHEMA IF NOT EXISTS auth;

-- Auth users table (demo users for all tenants)
CREATE TABLE IF NOT EXISTS auth.users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,  -- BCrypt hashed
    tenant_id   VARCHAR(50)  NOT NULL,
    role        VARCHAR(50)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Demo users (password = 'password123' hashed with BCrypt)
INSERT INTO auth.users (username, password, tenant_id, role) VALUES
    ('admin@toyota',      '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'toyota',     'ADMIN'),
    ('warehouse@toyota',  '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'toyota',     'WAREHOUSE'),
    ('production@toyota', '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'toyota',     'PRODUCTION'),
    ('auditor@toyota',    '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'toyota',     'AUDITOR'),
    ('supplier@toyota',   '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'toyota',     'SUPPLIER'),
    ('admin@honda',       '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'honda',      'ADMIN'),
    ('admin@mitsubishi',  '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'mitsubishi', 'ADMIN'),
    ('admin@daihatsu',    '$2a$12$8J.T6INV7oEI5bT9mH9.8uZhSSqX.sTOJc3g2PMxaDIhAzgmX5l3C', 'daihatsu',   'ADMIN')
ON CONFLICT (username) DO NOTHING;
