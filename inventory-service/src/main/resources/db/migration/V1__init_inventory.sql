-- ============================================================
-- Inventory Service — Flyway Migration V1
-- Runs for EACH tenant schema (toyota, honda, mitsubishi, etc.)
-- ============================================================

-- Materials master table
CREATE TABLE IF NOT EXISTS materials (
    id              BIGSERIAL      PRIMARY KEY,
    material_code   VARCHAR(50)    NOT NULL UNIQUE,
    material_name   VARCHAR(200)   NOT NULL,
    category        VARCHAR(100),
    uom             VARCHAR(20)    NOT NULL,
    min_stock_level DECIMAL(15, 3) NOT NULL DEFAULT 0,
    max_stock_level DECIMAL(15, 3) NOT NULL DEFAULT 9999999.999,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

-- Warehouses master table
CREATE TABLE IF NOT EXISTS warehouses (
    id             BIGSERIAL    PRIMARY KEY,
    warehouse_code VARCHAR(50)  NOT NULL UNIQUE,
    warehouse_name VARCHAR(200) NOT NULL,
    warehouse_type VARCHAR(50)  NOT NULL,
    location       VARCHAR(250),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP
);

-- Inventory balance table (with version for optimistic locking)
CREATE TABLE IF NOT EXISTS inventory (
    id                BIGSERIAL      PRIMARY KEY,
    material_id       BIGINT         NOT NULL REFERENCES materials(id),
    warehouse_id      BIGINT         NOT NULL REFERENCES warehouses(id),
    quantity          DECIMAL(15, 3) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(15, 3) NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP,
    CONSTRAINT uq_material_warehouse UNIQUE (material_id, warehouse_id)
);

-- Inventory transactions (idempotent immutable ledger)
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id                       BIGSERIAL      PRIMARY KEY,
    trx_no                   VARCHAR(50)    NOT NULL UNIQUE,
    idempotency_key          VARCHAR(100)   NOT NULL UNIQUE,
    material_id              BIGINT         NOT NULL REFERENCES materials(id),
    source_warehouse_id      BIGINT         REFERENCES warehouses(id),
    destination_warehouse_id BIGINT         REFERENCES warehouses(id),
    trx_type                 VARCHAR(50)    NOT NULL,
    quantity                 DECIMAL(15, 3) NOT NULL,
    supplier_code            VARCHAR(50),
    reference_no             VARCHAR(100),
    notes                    TEXT,
    performed_by             VARCHAR(100)   NOT NULL,
    trx_time                 TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Audit logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGSERIAL    PRIMARY KEY,
    entity_name  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(100),
    action_type  VARCHAR(50)  NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    tenant_id    VARCHAR(50)  NOT NULL,
    details      TEXT,
    timestamp    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_material_code ON materials(material_code);
CREATE INDEX IF NOT EXISTS idx_warehouse_code ON warehouses(warehouse_code);
CREATE INDEX IF NOT EXISTS idx_inventory_lookup ON inventory(material_id, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_trx_no ON inventory_transactions(trx_no);
CREATE INDEX IF NOT EXISTS idx_trx_idempotency ON inventory_transactions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_trx_material ON inventory_transactions(material_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant ON audit_logs(tenant_id);

-- Seed Data: Demo Warehouses
INSERT INTO warehouses (warehouse_code, warehouse_name, warehouse_type, location, active) VALUES
    ('WH-MAIN', 'Gudang Utama Raw Material', 'RAW_MATERIAL', 'Cikarang, Bekasi', true),
    ('WH-WIP', 'Gudang Work-in-Progress', 'WIP', 'Karawang, Jawa Barat', true),
    ('WH-FINISHED', 'Gudang Produk Jadi', 'FINISHED_GOODS', 'Sunter, Jakarta Utara', true)
ON CONFLICT (warehouse_code) DO NOTHING;

-- Seed Data: Demo Materials
INSERT INTO materials (material_code, material_name, category, uom, min_stock_level, max_stock_level, active) VALUES
    ('MAT-STEE-001', 'Cold Rolled Steel Coil 1.2mm', 'Steel', 'COIL', 10.0, 100.0, true),
    ('MAT-PLAS-002', 'Polypropylene Granules PP55', 'Plastic', 'KG', 1000.0, 50000.0, true),
    ('MAT-RUBB-003', 'Natural Rubber Compound Grade-A', 'Rubber', 'KG', 500.0, 10000.0, true),
    ('MAT-FAST-004', 'Hex Flange Bolt M8x1.25', 'Fastener', 'PCS', 5000.0, 100000.0, true),
    ('MAT-ALUM-005', 'Aluminium Alloy Ingot ADC12', 'Aluminium', 'TON', 5.0, 50.0, true)
ON CONFLICT (material_code) DO NOTHING;
