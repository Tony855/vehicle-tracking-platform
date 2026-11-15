CREATE TABLE device_enrollment_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    label VARCHAR(120) NOT NULL,
    max_devices INTEGER NOT NULL CHECK (max_devices BETWEEN 1 AND 10000),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (used_count <= max_devices),
    UNIQUE (tenant_id, id)
);
CREATE INDEX ix_device_enrollment_tokens_tenant ON device_enrollment_tokens(tenant_id, created_at DESC);
