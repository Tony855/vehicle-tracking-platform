# Device presence and location query API

All endpoints below require the device's own issued credential:

`Authorization: Bearer <device-credential>`

- `GET /api/v1/devices/{deviceUid}/presence`: reports the last authenticated telemetry time and considers a device online for 180 seconds after its last accepted authenticated report.
- `GET /api/v1/devices/{deviceUid}/locations/latest`: returns the newest recorded GPS point, or HTTP 204 when no point exists.
- `GET /api/v1/devices/{deviceUid}/locations?from=<ISO-8601>&to=<ISO-8601>&limit=500`: returns at most 1,000 points in reverse chronological order.

Every query first authenticates the device credential and scopes SQL by both the authenticated tenant ID and device ID. The caller cannot provide a tenant ID. Timestamps are UTC instants; coordinates are returned as WGS-84 latitude/longitude.

## Presence semantics
Presence is derived from `devices.last_seen_at`, updated after a successfully authenticated GPS report, including an idempotent retry. This is an HTTP telemetry heartbeat baseline, not yet an MQTT session presence signal. The 180-second threshold should become configurable when device reporting intervals and fleet requirements are finalized.

## Access boundary
These endpoints are device-self-service diagnostics, not the customer fleet dashboard API. Customer user authentication, role-based access and tenant-wide fleet queries are separate implementation work and must be completed before exposing fleet data to end users.
