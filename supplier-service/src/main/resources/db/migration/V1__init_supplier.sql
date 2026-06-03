-- ============================================================
-- Supplier Service — Flyway Migration V1
-- Runs for EACH tenant schema (toyota, honda, mitsubishi, etc.)
-- ============================================================

-- Suppliers master table
CREATE TABLE IF NOT EXISTS suppliers (
    id            BIGSERIAL     PRIMARY KEY,
    supplier_code VARCHAR(50)   NOT NULL UNIQUE,
    supplier_name VARCHAR(200)  NOT NULL,
    tax_number    VARCHAR(50),
    email         VARCHAR(100),
    phone         VARCHAR(20),
    address       TEXT,
    city          VARCHAR(100),
    country       VARCHAR(100)  DEFAULT 'Indonesia',
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    notes         TEXT,
    created_by    VARCHAR(100),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP,
    CONSTRAINT chk_supplier_status CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','BLACKLISTED'))
);

-- Supplier contacts
CREATE TABLE IF NOT EXISTS supplier_contacts (
    id            BIGSERIAL    PRIMARY KEY,
    supplier_id   BIGINT       NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    contact_name  VARCHAR(100) NOT NULL,
    contact_email VARCHAR(100),
    contact_phone VARCHAR(20),
    role          VARCHAR(50),
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_supplier_code   ON suppliers(supplier_code);
CREATE INDEX IF NOT EXISTS idx_supplier_status ON suppliers(status);
CREATE INDEX IF NOT EXISTS idx_contact_supplier ON supplier_contacts(supplier_id);

-- Seed data (demo suppliers for Toyota tenant)
INSERT INTO suppliers (supplier_code, supplier_name, tax_number, email, country, status, created_by) VALUES
    ('SUP-001', 'PT Baja Nusantara',    '01.234.567.8-000.000', 'sales@bajanusantara.co.id',   'Indonesia', 'ACTIVE',  'system'),
    ('SUP-002', 'PT Baut Jaya',         '02.345.678.9-000.000', 'contact@bautjaya.co.id',      'Indonesia', 'ACTIVE',  'system'),
    ('SUP-003', 'PT Plastik Prima',     '03.456.789.0-000.000', 'order@plastikprima.co.id',    'Indonesia', 'ACTIVE',  'system'),
    ('SUP-004', 'PT Karet Maju',        '04.567.890.1-000.000', 'info@karetmaju.co.id',        'Indonesia', 'PENDING', 'system'),
    ('SUP-005', 'PT Aluminium Sentosa', '05.678.901.2-000.000', 'sales@aluminiumsentosa.co.id','Indonesia', 'ACTIVE',  'system')
ON CONFLICT (supplier_code) DO NOTHING;
