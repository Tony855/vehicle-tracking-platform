CREATE TABLE geofences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(120) NOT NULL,
    area GEOGRAPHY(POLYGON,4326) NOT NULL,
    speed_limit_kmh NUMERIC(7,2) CHECK (speed_limit_kmh IS NULL OR speed_limit_kmh BETWEEN 1 AND 300),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id,id)
);
CREATE INDEX ix_geofences_tenant_active ON geofences(tenant_id,active);
CREATE INDEX ix_geofences_area ON geofences USING GIST(area);

CREATE TABLE alert_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    vehicle_id UUID,
    device_id UUID NOT NULL,
    geofence_id UUID,
    source_event_id UUID NOT NULL,
    alert_type VARCHAR(32) NOT NULL CHECK (alert_type IN ('GEOFENCE_SPEEDING')),
    message VARCHAR(240) NOT NULL,
    observed_speed_kmh NUMERIC(7,2),
    speed_limit_kmh NUMERIC(7,2),
    occurred_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (tenant_id,vehicle_id) REFERENCES vehicles(tenant_id,id),
    FOREIGN KEY (tenant_id,device_id) REFERENCES devices(tenant_id,id),
    FOREIGN KEY (tenant_id,geofence_id) REFERENCES geofences(tenant_id,id)
);
CREATE UNIQUE INDEX uq_alert_event_dedupe
    ON alert_events(device_id,source_event_id,alert_type,COALESCE(geofence_id,'00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX ix_alert_events_tenant_created ON alert_events(tenant_id,created_at DESC);
CREATE INDEX ix_alert_events_unacknowledged ON alert_events(tenant_id,occurred_at DESC) WHERE acknowledged_at IS NULL;
