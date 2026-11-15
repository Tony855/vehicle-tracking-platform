# Android smart head-unit GPS integration

Android smart screens / head units are a first-class tracker family in this platform. Register them with `hardwareType: ANDROID_HEADUNIT`; do not register them as generic phones.

## Device identity and enrollment

1. The installer or fleet administrator creates an enrollment token in the authenticated tenant console/API.
2. The head-unit application calls `POST /api/v1/devices/register` with its installation-generated stable `deviceUid`, the enrollment token and `ANDROID_HEADUNIT`.
3. The backend returns a 256-bit device credential once. Store it using Android Keystore-backed encrypted storage; never place it in logs, analytics, exported backups or a shared app preference.
4. Use the same credential for HTTPS GPS ingestion and MQTT device authentication. The device UID should be generated per app installation or provisioned by the manufacturer. Do not use IMEI, IMSI, phone number, Android serial number or other restricted hardware identifiers as the default identity.

Example registration body:

```json
{
  "enrollmentToken": "one-time-tenant-enrollment-token",
  "deviceUid": "headunit-installation-uuid",
  "hardwareType": "ANDROID_HEADUNIT"
}
```

## GNSS location collection

Use Android's fused location provider or the platform GNSS location APIs, preferring GNSS-quality fixes when available. The normalized telemetry contract is shared with ESP32 and dedicated 4G trackers:

- `latitude`, `longitude`: WGS-84 decimal degrees
- `recorded_at`: UTC ISO-8601 timestamp from the fix
- `speed_kmh`: km/h (convert Android Location speed in m/s by multiplying by 3.6)
- `heading_degrees`: degrees in [0, 360)
- `altitude_m`: metres
- `satellites`: satellite count when the platform exposes a trustworthy value
- `accuracy_m`: horizontal accuracy in metres
- `ignition_on`: nullable; report only when a reliable ACC / vehicle interface signal is available

Generate a new UUID `event_id` for each fix and persist unsent events locally. Retry after connectivity returns using the original event IDs so server-side deduplication remains effective. Keep the device clock in UTC and retain the original fix timestamp when uploading delayed events.

## Vehicle power and background operation

- Implement a foreground location service with a persistent notification where required by the Android version and deployment policy.
- Request location permissions progressively and explain continuous background tracking to the fleet operator/driver.
- Handle Doze, app standby, process recreation, reboot and network transitions. Use a bounded encrypted local queue and backoff; do not silently discard queued fixes.
- Read ACC / ignition only through an approved vehicle CAN adapter, GPIO/ACC wire, vendor SDK or documented head-unit integration. Do not infer ignition solely from screen on/off or Android power state.
- Report GNSS unavailable / stale-fix and permission-denied states as device status events rather than fabricating coordinates.
- Detect and flag mock/test provider locations when Android exposes that information; never represent simulated fixes as verified vehicle movement.

## MQTT and HTTPS

Use the shared topic shape:

`v1/tenants/{tenantId}/devices/{deviceId}/location`

The device must use the exact tenant/device IDs returned by a trusted enrollment or provisioning response; server ACL still checks ownership against the device database. Publish location with QoS 1. Production remote connectivity requires the platform's TLS listener and certificate policy to be enabled first. The current Compose MQTT listener is localhost-only plaintext for development and is not suitable for remote head units.

## Validation checklist

- Test GNSS cold start, warm start, tunnel/garage loss and recovery.
- Verify speed conversion (m/s to km/h), UTC timestamps and heading normalization.
- Verify no duplicate rows after MQTT QoS redelivery or offline queue replay.
- Verify device credential remains unavailable to other Android apps and is not logged.
- Verify app process restart and reboot resume queued uploads.
- Verify ACC unknown remains null rather than false when no reliable signal exists.
