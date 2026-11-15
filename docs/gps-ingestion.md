# GPS location ingestion

## Endpoint

`POST /api/v1/telemetry/locations`

Required header:

```http
Authorization: Bearer <device-credential>
Content-Type: application/json
```

The request body follows the normalized v1 contract in `telemetry-contract.md`. It includes the device UID and event ID but does not include a tenant ID.

## Processing
1. The API verifies the bearer credential against the SHA-256 hash stored for an ACTIVE device.
2. The authenticated device row supplies the device ID and tenant ID. A payload cannot select or override its tenant.
3. Coordinates are converted from WGS-84 longitude/latitude to PostGIS geography.
4. The database unique key on `(device_id, event_id)` makes retry delivery idempotent.
5. The API returns `accepted` for a newly inserted event and `duplicate` when the same event has already been stored.

Invalid/missing device credentials return HTTP 401. Bean validation rejects malformed coordinates, invalid speed/heading, missing identifiers and unsupported schema versions.

## Operational requirements
- Expose this endpoint only over HTTPS.
- Apply request-size limits and per-device rate limits at the reverse proxy/API gateway.
- Keep device credentials in Android Keystore, secure MCU storage where available, or an appropriately protected modem/terminal credential store.
- Monitor rejected credentials, database insert latency, duplicate rates and device clock drift.
- This implementation is the synchronous ingestion baseline. MQTT consumer integration, asynchronous buffering, telemetry retention/partitioning, and live fanout are subsequent work.
