# Geofences and speeding alerts

All endpoints require a tenant-user JWT. Data access is always scoped by the tenant ID in that verified token.

## Geofence management

- `POST /api/v1/geofences` — create a polygon geofence.
- `GET /api/v1/geofences` — list tenant geofences.
- `DELETE /api/v1/geofences/{geofenceId}` — disable a geofence without deleting its history.

Example request:

```json
{
  "name": "Depot",
  "polygonWkt": "POLYGON((104.92 11.55,104.94 11.55,104.94 11.57,104.92 11.57,104.92 11.55))",
  "speedLimitKmh": 30
}
```

Polygon coordinates are WKT in WGS-84 longitude/latitude order. The API validates polygon geometry, topology and coordinate bounds. Speed limit is optional; when set, it must be 1–300 km/h.

## Speeding alert generation

When a newly accepted GPS event falls inside an active geofence with a configured speed limit and its speed exceeds that limit, the ingestion transaction creates a `GEOFENCE_SPEEDING` alert. Duplicate GPS event delivery does not create duplicate alerts. Disabled geofences stop generating new alerts; existing alerts remain available.

## Alert inbox

- `GET /api/v1/alerts?unacknowledgedOnly=true&limit=100&offset=0` — list alerts.
- `POST /api/v1/alerts/{alertId}/acknowledge` — acknowledge an alert.

OWNER, ADMIN, DISPATCHER and VIEWER can read alerts. Only OWNER, ADMIN and DISPATCHER can create/disable geofences or acknowledge alerts. The first release generates speeding alerts only; geofence entry/exit, overspeed duration thresholds, notifications and escalation policies remain future work.
