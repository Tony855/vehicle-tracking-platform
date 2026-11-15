# Device enrollment

## Flow
1. An authorized operator provisions a tenant-scoped enrollment token in the database or a future tenant-admin API.
2. Store only the SHA-256 hash of the random enrollment token in `device_enrollment_tokens.token_hash`.
3. A device calls `POST /api/v1/devices/register` with `enrollmentToken`, `deviceUid`, and `hardwareType`.
4. The API locks and checks the token row, expiry, revocation and remaining device quota in one transaction.
5. The API creates the device under the tenant attached to that token and returns a random 256-bit device credential exactly once. Only its SHA-256 hash is persisted.
6. The device must store the credential in platform-protected storage and use TLS for subsequent authenticated requests.

## Create a token (operator-only bootstrap procedure)
Generate a random token on a trusted workstation:
```sh
TOKEN="$(openssl rand -base64 36 | tr '+/' '-_' | tr -d '=')"
printf '%s' "$TOKEN" | sha256sum
```
Insert the resulting 64-character SHA-256 hex digest (not the plaintext token) for an existing tenant:
```sql
INSERT INTO device_enrollment_tokens
  (tenant_id, token_hash, label, max_devices, expires_at)
VALUES
  ('<EXISTING_TENANT_UUID>', '<SHA256_HEX>', 'Initial device batch', 20, now() + interval '7 days');
```
Deliver the plaintext token to the intended installer through a secure channel. Never put it in source control, tickets, logs or chat.

## API example
```http
POST /api/v1/devices/register
Content-Type: application/json
```
```json
{
  "enrollmentToken": "<one-time-shared-enrollment-secret>",
  "deviceUid": "GPS-UNIT-00001",
  "hardwareType": "GPS_4G_TERMINAL"
}
```

The response includes a one-time `credential`. The server-side `DeviceCredentialService` verifies subsequent credentials against stored hashes and returns the authenticated device/tenant identity without exposing the hash. Disabled or revoked devices cannot authenticate. The API does not accept a client-supplied tenant ID. Enrollment token quota consumption and device creation are transactional. A duplicate device UID returns HTTP 409; invalid, expired, revoked or exhausted tokens return HTTP 401.

## Production requirements still outstanding
- Put the API behind HTTPS and rate-limit registration attempts at the gateway.
- Build authenticated tenant-admin endpoints for token issuance, revocation and audit.
- Add device credential rotation/revocation and authenticated device request middleware before enabling telemetry ingestion.
- Do not use this bootstrap SQL procedure as a permanent self-service workflow.
