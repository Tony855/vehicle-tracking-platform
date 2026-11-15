CREATE TABLE tenant_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN ('OWNER','ADMIN','DISPATCHER','VIEWER')),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, email)
);
CREATE INDEX ix_tenant_users_email ON tenant_users(lower(email));

ALTER TABLE vehicles
    ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','ARCHIVED')),
    ADD COLUMN make VARCHAR(80),
    ADD COLUMN model VARCHAR(80),
    ADD COLUMN year SMALLINT CHECK (year IS NULL OR year BETWEEN 1900 AND 2200),
    ADD COLUMN color VARCHAR(40),
    ADD COLUMN notes VARCHAR(1000);

CREATE INDEX ix_vehicles_tenant_created ON vehicles(tenant_id, created_at DESC);
CREATE INDEX ix_device_bindings_tenant_active ON device_bindings(tenant_id, bound_at DESC)
    WHERE unbound_at IS NULL;
