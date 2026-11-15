# Telemetry contract v1

Canonical location event:

    {
      "schema_version": 1,
      "event_id": "b6f9f1aa-7d84-4c48-9d55-6e70b6ddf2c2",
      "device_uid": "DEVICE-UNIQUE-ID",
      "recorded_at": "2026-01-15T10:20:30Z",
      "latitude": 11.5564,
      "longitude": 104.9282,
      "speed_kmh": 42.5,
      "heading_degrees": 180.0,
      "altitude_m": 12.4,
      "satellites": 17,
      "accuracy_m": 3.2,
      "ignition_on": true
    }

Rules:
- Coordinates are WGS-84 decimal degrees; latitude [-90, 90], longitude [-180, 180].
- Timestamps are UTC RFC 3339; server records receive time separately.
- Speed is km/h; heading is [0, 360).
- Event ID remains stable across retries and provides idempotency.
- Tenant identity is resolved from authenticated device registration, never trusted from payload.
- Offline devices buffer events and replay oldest-first after reconnect; server deduplicates by device and event ID.
