ALTER TABLE devices ADD COLUMN last_seen_at TIMESTAMPTZ;
CREATE INDEX ix_devices_tenant_last_seen ON devices(tenant_id, last_seen_at DESC);
