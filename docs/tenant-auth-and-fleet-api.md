# Tenant authentication and fleet management API

All endpoints below are under `/api/v1`. Tenant-scoped endpoints derive `tenant_id` from the verified JWT; clients must not submit tenant IDs to select another tenant.

## Provision first tenant

A trusted operator provisions the first tenant using the out-of-band bootstrap secret. Keep this secret offline and rotate/remove it after initial provisioning.

`POST /bootstrap/tenants`

Header: `X-Bootstrap-Secret: <TENANT_BOOTSTRAP_SECRET>`

Body:

```json
{
  "tenantName": "Example Fleet",
  "ownerEmail": "owner@example.com",
  "ownerPassword": "a-long-unique-password",
  "ownerName": "Fleet Owner"
}
```

## Login

`POST /auth/login`

Body: `{"email":"owner@example.com","password":"..." }`

Returns a one-hour HS256 bearer token plus user and tenant identifiers. Send it as `Authorization: Bearer <accessToken>`. The current release does not yet implement refresh tokens, password reset or MFA.

## Tenant users

- `GET /users`: list users in the caller's tenant (OWNER / ADMIN).
- `POST /users`: create ADMIN, DISPATCHER or VIEWER user (OWNER / ADMIN).
- `PATCH /users/{userId}/status`: enable or disable a non-owner user (OWNER / ADMIN).

Passwords must contain at least 12 characters and are stored using BCrypt. Email addresses are globally unique to keep email-only login unambiguous.

## Vehicle fleet

- `POST /vehicles`: create a vehicle profile.
- `GET /vehicles`: list vehicles in the caller's tenant, including active device binding summary.
- `POST /vehicles/{vehicleId}/device`: bind an ACTIVE device belonging to the same tenant.
- `DELETE /vehicles/{vehicleId}/device`: end the active binding without deleting historical binding records.

Database foreign keys enforce same-tenant ownership. Partial unique indexes prevent one active device being bound to multiple vehicles and one active vehicle receiving multiple devices. Concurrent binding attempts are handled as HTTP 409 conflicts.

## Device enrollment tokens

- `POST /device-enrollment-tokens`: create a token (OWNER / ADMIN); raw token is returned once.
- `GET /device-enrollment-tokens`: list token metadata without hashes or raw secrets.
- `POST /device-enrollment-tokens/{tokenId}/revoke`: revoke a token.

Example create body: `{"label":"Android head units - batch 1","maxDevices":50,"expiresAt":"2027-01-01T00:00:00Z"}`.

## Android head-unit devices

Register an Android smart screen using `hardwareType: ANDROID_HEADUNIT`. See [Android head-unit GPS integration](android-headunit-gps-integration.md) for installation identity, GNSS, background service, offline queue, ACC and validation requirements.

## Security and operational notes

- Set `JWT_SECRET` to a unique random value of at least 32 characters. The in-code fallback is for local development only.
- Set `TENANT_BOOTSTRAP_SECRET` to a unique random value of at least 32 characters. The bootstrap endpoint is not safe to expose without this secret.
- Put the API behind HTTPS in any shared or production environment.
- Current JWT lifetime is one hour. Refresh tokens, password reset, MFA, audit log and fine-grained per-action roles remain future work.
- This release provides the API foundation; a production web console and Flutter Android head-unit app are not yet delivered.
