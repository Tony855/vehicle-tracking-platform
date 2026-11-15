# Fleet location query API

All fleet endpoints require a valid tenant-user JWT. The tenant ID is taken from the verified JWT claim; clients cannot select another tenant by query parameter or request body.

## Latest fleet positions

`GET /api/v1/fleet/locations/latest?limit=500`

Returns the latest accepted GPS point for each active vehicle that has an active device binding and at least one stored location. The result includes vehicle identity, device UID and hardware type, event ID, UTC fix timestamp, WGS-84 latitude/longitude, speed in km/h, heading, altitude, satellite count, accuracy and nullable ignition state.

The endpoint covers all supported device families, including:
- `ANDROID_HEADUNIT` — Android smart head-unit screen
- `ESP32_GNSS` — ESP32 GNSS tracker
- `GPS_4G_TERMINAL` — dedicated cellular GPS terminal

The limit defaults to 500 and is capped at 1000. The query is restricted to the authenticated tenant.

## Vehicle track history

`GET /api/v1/fleet/vehicles/{vehicleId}/locations?from=2026-11-15T00:00:00Z&to=2026-11-15T23:59:59Z&limit=500&offset=0`

Returns GPS history for the specified vehicle in the authenticated tenant. It includes points from historical device bindings only during the time each binding was active, so a replacement device does not rewrite the vehicle's earlier track. Results are newest-first; use limit/offset for pagination. Limit is capped at 1000. The API rejects inverted time ranges.

## Device-level diagnostics

Device credential endpoints remain available separately:
- `GET /api/v1/devices/{deviceUid}/locations/latest`
- `GET /api/v1/devices/{deviceUid}/locations?from=...&to=...&limit=...`
- `GET /api/v1/devices/{deviceUid}/presence`

These endpoints authenticate with the device's own bearer credential, not the tenant-user JWT.

## Data freshness and map behavior

The latest fleet endpoint returns the most recently recorded fix, not a claim that the vehicle is currently online. Use the device presence endpoint / device inventory last-seen timestamp for online state. A stale GPS point must be displayed with its timestamp and stale indicator by the client rather than presented as live movement.
